package template

import org.jetbrains.dokka.DokkaConfiguration
import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.driOrNull
import org.jetbrains.dokka.base.translators.documentables.PageContentBuilder
import org.jetbrains.dokka.links.Nullable
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.pages.*
import org.jetbrains.dokka.links.parent
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.Projection
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.query
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.renderers.Renderer
import org.jetbrains.dokka.transformers.documentation.DocumentableToPageTranslator
import org.jetbrains.dokka.utilities.DokkaLogger
import sun.util.logging.resources.logging_es
import java.io.File

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
        val signatureFile = File(outputDir, "all-func.txt")
        signatureFile.printWriter().use { writer ->
            // Walk the page tree and collect signatures
            logger.progress("Found root")
            logger.progress(root.name)


            if (root is ModulePageNode) {
                root.documentables.first().collectFunctionSignatures().forEach {
                    writer.println(it)
                }
            }

        }
        logger.info("Flatfile with ${signatureFile.readLines().size} signatures written to ${signatureFile.absolutePath}")
    }

    private fun Documentable.collectFunctionSignatures(): List<String> {
        val signatures = mutableListOf<String>()
        if (this is DModule) {
            this.packages.flatMap { it.functions }.forEach {
                if (it.receiver?.type?.driOrNull?.classNames == "Container") {
                    signatures.addAll(it.collectFunctionSignatures())
                }
            }
        }
        else if (this is DFunction) {
            var parametersStrList = mutableListOf<String>()

            this.parameters.forEach {
                if (it.type is org.jetbrains.dokka.model.Nullable) {
                    parametersStrList.add("${it.name}: ${it.type.driOrNull?.classNames.orEmpty()}?")
                } else {

                    parametersStrList.add("${it.name}: ${it.type.driOrNull?.classNames.orEmpty()}")
                }
            }
            val parametersStr = parametersStrList.joinToString(", ")

            signatures.add("fun Container.${this.name}(${parametersStr})")

        } else if (this is DParameter) {
//            this.type.driOrNull?.let {
//                signatures.add(it.toString())
//            }
        }
        return signatures
    }

    /** Recursively traverse pages and extract function signatures */
    private fun PageNode.collectFunctionSignatures(): List<String> {
        val signatures = mutableListOf<String>()
        when (this) {
            is MemberPageNode -> {
//                if (this.name == "BenefitCard") {
                    logger.progress("${ this.name } is member")
                    // PageNode.name contains the declaration name; signature formatting may vary
                    logger.progress("kind :${content.dci.kind}")
                    if (content is ContentDRILink) {
                        logger.progress("and content is DRILink")
                        signatures.add((content as ContentDRILink).address.packageName ?: "")
                    } else {
//                        logger.progress("content is $content")
                    }
//                }

            }
            is ModulePageNode -> {
                logger.progress("Found module!! ${this.name}")
                this.documentables.forEach { it
                    if (it is DModule) {
                        val extraStr = it.extra[CustomExtra]?.customExtraValue ?: ""
                        logger.progress("found extra str $extraStr")
                    }
                }
                children.forEach { signatures += it.collectFunctionSignatures() }
            }
            else -> children.forEach { signatures += it.collectFunctionSignatures() }
        }
        return signatures
    }
}

