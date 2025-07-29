package template

import org.jetbrains.dokka.CoreExtensions
import org.jetbrains.dokka.base.DokkaBase
import org.jetbrains.dokka.plugability.DokkaPlugin
import org.jetbrains.dokka.plugability.PluginApiPreviewAcknowledgement
import org.jetbrains.dokka.plugability.DokkaPluginApiPreview
import org.jetbrains.dokka.plugability.Extension
import org.jetbrains.dokka.renderers.Renderer

class MyAwesomeDokkaPlugin : DokkaPlugin() {
    private val dokkaBasePlugin: DokkaBase by lazy { plugin<DokkaBase>() }

    @DokkaPluginApiPreview
    override fun pluginApiPreviewAcknowledgement() = PluginApiPreviewAcknowledgement

    public val dokkaJavadocPlugin: Extension<Renderer, *, *> by extending {
        CoreExtensions.renderer providing { ctx -> FlatFileRenderer(ctx
        ) } override dokkaBasePlugin.htmlRenderer
    }



}