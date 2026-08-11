package com.krystelligence.solipsism.i18n

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources
import android.util.Xml
import androidx.core.content.edit
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.util.Locale

/** Safe, private runtime overrides imported from the documented custom XML format. */
object TranslationOverrides {
    private const val PREFERENCES = "custom_language"
    private const val JSON_KEY = "strings"
    private const val MAX_BYTES = 512 * 1024
    private const val MAX_STRINGS = 512
    private const val MAX_VALUE_LENGTH = 16 * 1024

    fun wrap(context: Context): Context {
        val overrides = read(context)
        if (overrides.isEmpty()) return context
        return object : ContextWrapper(context) {
            private val overlayResources = OverlayResources(context.resources, overrides)
            override fun getResources(): Resources = overlayResources
        }
    }

    fun import(context: Context, input: InputStream): Int {
        val bytes = input.use(::readLimited)
        val header = bytes.toString(Charsets.UTF_8).uppercase(Locale.ROOT)
        require("<!DOCTYPE" !in header && "<!ENTITY" !in header) {
            "DOCTYPE and ENTITY declarations are not supported"
        }
        val parser = Xml.newPullParser().apply {
            runCatching { setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            setInput(bytes.inputStream(), Charsets.UTF_8.name())
        }
        val parsed = linkedMapOf<String, String>()
        var event = parser.eventType
        var rootSeen = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when {
                    !rootSeen -> {
                        require(parser.name == "resources") { "root element must be resources" }
                        rootSeen = true
                    }
                    parser.name == "string" -> {
                        require(parser.namespace.isEmpty()) { "namespaces are not supported" }
                        val name = parser.getAttributeValue(null, "name")
                        require(!name.isNullOrBlank() && RESOURCE_NAME.matches(name)) { "invalid string name" }
                        require(!parsed.containsKey(name)) { "duplicate string: $name" }
                        require(parsed.size < MAX_STRINGS) { "too many strings" }
                        val id = context.resources.getIdentifier(name, "string", context.packageName)
                        require(id != 0) { "unknown string: $name" }
                        val value = parser.nextText()
                        require(value.length <= MAX_VALUE_LENGTH) { "string is too long: $name" }
                        validatePlaceholders(context.resources.getString(id), value, name)
                        parsed[name] = value
                    }
                    parser.name != "resources" -> throw IllegalArgumentException("only string elements are supported")
                }
                XmlPullParser.END_TAG -> if (parser.name == "resources") rootSeen = true
            }
            event = parser.next()
        }
        require(rootSeen) { "missing resources root" }
        require(parsed.isNotEmpty()) { "no string resources found" }
        save(context, parsed)
        return parsed.size
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit { clear() }
    }

    fun count(context: Context): Int = read(context).size

    private fun validatePlaceholders(base: String, replacement: String, name: String) {
        val pattern = Regex("%[0-9]+\\\$[sd]")
        require(pattern.findAll(base).map { it.value }.toSet() == pattern.findAll(replacement).map { it.value }.toSet()) {
            "placeholder mismatch in $name"
        }
    }

    private fun read(context: Context): Map<String, String> = runCatching {
        val value = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(JSON_KEY, null) ?: return emptyMap()
        val json = JSONObject(value)
        val strings = json.getJSONObject(JSON_KEY)
        strings.keys().asSequence().associateWith { strings.getString(it) }
    }.getOrDefault(emptyMap())

    private fun save(context: Context, values: Map<String, String>) {
        val strings = JSONObject()
        values.forEach { (name, value) -> strings.put(name, value) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit {
            putString(JSON_KEY, JSONObject().put(JSON_KEY, strings).toString())
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_BYTES) { "file is larger than 512 KB" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private class OverlayResources(
        base: Resources,
        private val overrides: Map<String, String>
    ) : Resources(base.assets, base.displayMetrics, base.configuration) {
        override fun getText(id: Int): CharSequence = override(id) ?: super.getText(id)
        override fun getString(id: Int): String = override(id) ?: super.getString(id)
        override fun getString(id: Int, vararg formatArgs: Any): String =
            String.format(Locale.getDefault(), getString(id), *formatArgs)

        private fun override(id: Int): String? = runCatching {
            overrides[getResourceEntryName(id)]
        }.getOrNull()
    }

    private val RESOURCE_NAME = Regex("[a-z][a-z0-9_]*")
}
