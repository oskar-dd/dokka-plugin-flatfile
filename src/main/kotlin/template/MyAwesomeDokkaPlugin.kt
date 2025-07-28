package template

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.base.DokkaBaseConfiguration
import org.jetbrains.dokka.base.signatures.KotlinSignatureUtils.driOrNull
import org.jetbrains.dokka.base.translators.documentables.DefaultDocumentableToPageTranslator
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.model.DFunction
import org.jetbrains.dokka.model.DModule
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.SourceSetDependent
import org.jetbrains.dokka.model.properties.ExtraProperty
import org.jetbrains.dokka.model.properties.MergeStrategy
import org.jetbrains.dokka.model.properties.PropertyContainer
import org.jetbrains.dokka.pages.ModulePageNode
import org.jetbrains.dokka.plugability.DokkaContext
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.plugability.configuration
import org.jetbrains.dokka.plugability.plugin
import org.jetbrains.dokka.plugability.query
import org.jetbrains.dokka.plugability.querySingle
import org.jetbrains.dokka.renderers.Renderer
import org.jetbrains.dokka.transformers.documentation.DocumentableToPageTranslator
import org.jetbrains.dokka.transformers.documentation.DocumentableTransformer
import org.jetbrains.dokka.base.translators.documentables.DefaultPageCreator
import org.jetbrains.dokka.pages.ContentKind
import org.jetbrains.dokka.pages.ContentNode
import org.jetbrains.dokka.pages.ContentText
import org.jetbrains.dokka.pages.DCI
import org.jetbrains.dokka.pages.Kind
import org.jetbrains.dokka.utilities.DokkaLogger
import kotlin.collections.component1
import kotlin.collections.component2

class MyAwesomeDokkaPlugin : DokkaPlugin() {
    private val dokkaBasePlugin: DokkaBase by lazy { plugin<DokkaBase>() }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement

    val containerFunctionCollector: Extension<DocumentableTransformer, *, *>  by extending {
        CoreExtensions.documentableTransformer with ContainerFunctionCollector()
    }


    public val dokkaJavadocPlugin: Extension<Renderer, *, *> by extending {
        CoreExtensions.renderer providing { ctx -> FlatFileRenderer(ctx
        ) } override dokkaBasePlugin.htmlRenderer
    }



}
class ContainerFunctionCollector : DocumentableTransformer {


    override fun invoke(
        original: DModule,
        context: DokkaContext
    ): DModule {
        original.packages.forEach { pkg ->
            pkg.functions.filter { fn ->
                fn.receiver?.type?.driOrNull?.classNames == "Container"
            }.forEach { fn ->
                context.logger.progress("Found some container ${fn.name}")

            }
        }
        val newExtras = original.extra.addAll(listOf(CustomExtra("Container")))
        return original.copy(extra = newExtras)
    }


}
data class CustomExtra(val customExtraValue: String) : ExtraProperty<DModule> {
    override val key: ExtraProperty.Key<Documentable, *> = CustomExtra
    companion object: ExtraProperty.Key<Documentable, CustomExtra>
}
//
//public class KotlinAsJavaDocumentableToPageTranslator(
//    context: DokkaContext
//) : DocumentableToPageTranslator {
//    private val configuration = configuration<DokkaBase, DokkaBaseConfiguration>(context)
//    private val commentsToContentConverter = context.plugin<DokkaBase>().querySingle { commentsToContentConverter }
//    private val signatureProvider = context.plugin<DokkaBase>().querySingle { signatureProvider }
//    private val customTagContentProviders = context.plugin<DokkaBase>().query { customTagContentProvider }
//    private val documentableSourceLanguageParser = context.plugin<InternalKotlinAnalysisPlugin>().querySingle { documentableSourceLanguageParser }
//    private val logger: DokkaLogger = context.logger
//
//    override fun invoke(module: DModule): ModulePageNode =
//        KotlinAsJavaPageCreator(
//            configuration,
//            commentsToContentConverter,
//            signatureProvider,
//            logger,
//            customTagContentProviders,
//            documentableSourceLanguageParser
//        ).pageForModule(module)
//}

class ComponentOnlyTranslator(context: DokkaContext) : DocumentableToPageTranslator {

    val defaultTranslator = DefaultDocumentableToPageTranslator(context)
    override fun invoke(module: DModule): ModulePageNode
        {
            val page = defaultTranslator.invoke(module)
            return page
    }
}
