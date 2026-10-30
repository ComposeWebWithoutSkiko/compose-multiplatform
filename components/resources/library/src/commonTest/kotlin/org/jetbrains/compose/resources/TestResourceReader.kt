package org.jetbrains.compose.resources

internal class TestResourceReader : ResourceReader {
    private val readPathsList = mutableListOf<String>()
    val readPaths: List<String> get() = readPathsList

    override suspend fun read(path: String): ByteArray {
        readPathsList.add(path)
        return DefaultResourceReader.read(path)
    }

    override suspend fun readPart(path: String, offset: Long, size: Long): ByteArray {
        readPathsList.add("$path/$offset-$size")
        return DefaultResourceReader.readPart(path, offset, size)
    }

    // On the web, the browser loads images and fonts from here.
    override fun getUri(path: String): String {
        readPathsList.add(path)
        return DefaultResourceReader.getUri(path)
    }
}