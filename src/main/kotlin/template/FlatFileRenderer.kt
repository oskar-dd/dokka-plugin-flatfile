package template

import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.dri
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.driOrNull
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.model.DClass
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DefinitelyNonNullable
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.Dynamic
import org.jetbrains.dokka.model.FunctionalTypeConstructor
import org.jetbrains.dokka.model.GenericTypeConstructor
import org.jetbrains.dokka.model.JavaObject
import org.jetbrains.dokka.model.PrimitiveJavaType
import org.jetbrains.dokka.model.Projection
import org.jetbrains.dokka.model.Star
import org.jetbrains.dokka.model.TypeAliased
import org.jetbrains.dokka.model.TypeParameter
import org.jetbrains.dokka.model.UnresolvedBound
import org.jetbrains.dokka.model.Variance
import org.jetbrains.dokka.model.Void
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.renderers.Renderer
import org.jetbrains.dokka.utilities.DokkaLogger
import java.io.File
import kotlin.collections.plus

/**
 * A Dokka Renderer that outputs all function signatures into a single flat file.
 */
class FlatFileRenderer(
    private val context: DokkaContext,
    private val logger: DokkaLogger = context.logger
) : Renderer {

    override fun render(root: RootPageNode) {
        // Determine output directory from configuration
        val outputDir = context.configuration.outputDir
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            logger.warn("Could not create output directory: ${outputDir.absolutePath}")
        }

        // Prepare the flatfile
        val signatureFile = File(outputDir, "all_func.csv")
        signatureFile.printWriter().use { writer ->
            // Walk the page tree and collect signatures
            logger.progress("Found root")
            logger.progress(root.name)

            writer.println("signature, package_path")
            if (root is ModulePageNode) {
                val index = buildDocumentableIndex(root.documentables.first())
                logger.progress("index built ${index.keys}")
                val allFunctionSigs = root.documentables.first().collectFunctionSignatures(index).toTypedArray()
                setOf(*allFunctionSigs)
                    .toTypedArray()
                    .sortedBy { it.second } // ranked alphabetically by package path name
                    .forEach {
                        writer.println("${it.first}, ${it.second}")
                }
            }

        }
        logger.info("Flatfile with ${signatureFile.readLines().size} signatures written to ${signatureFile.absolutePath}")
    }

    private fun buildDocumentableIndex(root: Documentable): Map<DRI, Documentable> {
        return if (root is DModule) {
            root.packages.flatMap {it.functions + it.properties + it.classlikes }.map { buildDocumentableIndex(it)}.fold(
                mutableMapOf<DRI, Documentable>()) { acc, map ->
                acc += map
                acc
            }
        } else {
            mapOf(Pair(root.dri, root))
        }
    }

    // Recursively look for Container.function and their parameter type's constructors
    private fun Documentable.collectFunctionSignatures(index: Map<DRI, Documentable>): List<Pair<String, String>> {
        val signatures = mutableListOf<Pair<String, String>>()
        if (this is DModule) {
            this.packages.flatMap { it.functions }.forEach {
                if (it.receiver?.type?.driOrNull?.classNames == "Container") {
                    signatures.addAll(it.collectFunctionSignatures(index))
                }
            }
        }
        else if (this is DFunction) {
            var parametersStrList = mutableListOf<String>()

            this.parameters.forEach {

                val sig = signatureForProjection(it.type)
                parametersStrList.add("${it.name}: $sig")
            }
            val parametersStr = parametersStrList.joinToString(", ")

            if (this.receiver?.type?.driOrNull?.classNames == "Container") {
                signatures.add(Pair("fun Container.${this.name}(${parametersStr})", this.dri.packageName.orEmpty()))
            } else {
                // otherwise we output constructor
                signatures.add(Pair("class ${this.name}(${parametersStr})",this.dri.packageName.orEmpty()))
            }
            signatures.addAll(this.parameters.flatMap { it.collectFunctionSignatures(index) })
        } else if (this is DParameter) {
            logger.progress("doing params now for ${this.name}")
            this.type.driOrNull.let {
                val property = index[it]
                if (property != null) {
                    logger.progress("property hit")
                } else {
                    logger.progress("property miss for $it")
                }
                signatures.addAll(property?.collectFunctionSignatures(index) ?: emptyList())
            }
        } else if (this is DClass) {
            signatures.addAll(this.constructors.flatMap { it.collectFunctionSignatures(index) })
        }
        return signatures
    }

    // This is copied from KotlinSignatureProvider in dokka source code but instead of output DSL, we converted everything to flat strings
    private fun signatureForProjection(
        p: Projection, showFullyQualifiedName: Boolean = false
    ): String {
        return when (p) {
            is TypeParameter -> {
                var res = ""

                if (p.presentableName != null) {
                    res += p.presentableName!!
                    res += ": "
                }
                res += p.name
                res
            }
            is FunctionalTypeConstructor -> {
                var res = ""
                if (p.presentableName != null) {
                    res += p.presentableName!! + ": "
                }

                if (p.isSuspendable) res += "suspend "

                val projectionsWithoutContextParameters = p.projections.drop(0)

                if (p.isExtensionFunction) {
                    logger.progress("IS EXTENSION")
                    val sig = signatureForProjection(projectionsWithoutContextParameters.first())

                    logger.progress("sig of extension func $sig")
                    res += sig
                    res += "."
                }

                val args = if (p.isExtensionFunction)
                    projectionsWithoutContextParameters.drop(1)
                else
                    projectionsWithoutContextParameters

                res += "("
                if(args.isEmpty()) {
                    logger.warn("Functional type should have at least one argument in ${p.dri}")
                    return "ERROR CLASS: functional type should have at least one argument in ${p.dri}"
                }

                args.subList(0, args.size - 1).forEachIndexed { i, arg ->
                    res += signatureForProjection(arg)
                    if (i < args.size - 2) res += ", "
                }
                res += ")"
                res += " -> "
                res += signatureForProjection(args.last())
                res
            }
            is GenericTypeConstructor -> {
                var res = ""
                val linkText = if (showFullyQualifiedName && p.dri.packageName != null) {
                    "${p.dri.packageName}.${p.dri.classNames.orEmpty()}"
                } else p.dri.classNames.orEmpty()
                val presentableName = if (p.presentableName != null) {
                    p.presentableName!! + ": "
                } else ""
                res += presentableName
                res += linkText

                res += if (p.projections.count() > 0) p.projections.joinToString(", ", "<", ">") { signatureForProjection(it) } else ""
                res
                }

            is Variance<*> -> {
                p.takeIf { it.toString().isNotEmpty() }?.let { "$it " }
                signatureForProjection(p.inner, showFullyQualifiedName)
            }

            is Star -> "*"

            is org.jetbrains.dokka.model.Nullable -> {
                signatureForProjection(p.inner, showFullyQualifiedName) + "?"
            }
            is DefinitelyNonNullable -> {
                signatureForProjection(p.inner, showFullyQualifiedName) + " & " + "Any"
            }

            is TypeAliased -> signatureForProjection(p.typeAlias)
            is JavaObject -> {
                return "Any"
            }
            is Void -> "Unit"
            is PrimitiveJavaType -> signatureForProjection(p.translateToKotlin(), showFullyQualifiedName)
            is Dynamic -> "dynamic"
            is UnresolvedBound -> p.name
        }

    }
}

private fun PrimitiveJavaType.translateToKotlin() = GenericTypeConstructor(
    dri = dri,
    projections = emptyList(),
    presentableName = null
)
