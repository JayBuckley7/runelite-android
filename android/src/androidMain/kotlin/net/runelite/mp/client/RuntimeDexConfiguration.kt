package net.runelite.mp.client

import android.content.Context
import com.android.tools.r8.D8Command
import java.io.File
import java.security.MessageDigest
import java.util.Properties

/** Runtime conversions must use the same core-library ABI as the host APK. */
internal object RuntimeDexConfiguration
{
    const val CACHE_VERSION = "corelib214-v1"

    fun configure(context: Context, builder: D8Command.Builder, workDir: File): D8Command.Builder
    {
        val manifest = Properties().apply {
            context.assets.open("runtime-dex/inputs.properties").use { load(it) }
        }
        check(manifest.getProperty("version") == "2.1.4") { "Unexpected runtime desugaring configuration" }
        fun verifiedAsset(name: String): ByteArray
        {
            val bytes = context.assets.open("runtime-dex/$name").use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(digest == manifest.getProperty("sha256.$name")) { "Runtime compiler asset mismatch: $name" }
            return bytes
        }
        builder.addDesugaredLibraryConfiguration(verifiedAsset("desugar.json").toString(Charsets.UTF_8))
        for (name in listOf("java-api.jar", "host-api.jar"))
        {
            val file = File(workDir, name)
            file.writeBytes(verifiedAsset(name))
            if (name == "java-api.jar") builder.addLibraryFiles(file.toPath())
            else builder.addClasspathFiles(file.toPath())
        }
        return builder.setMinApiLevel(26)
    }
}
