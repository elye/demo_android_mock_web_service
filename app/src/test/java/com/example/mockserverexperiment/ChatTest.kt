package com.example.mockserverexperiment

import okhttp3.HttpUrl
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.*
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

class ChatTest {

    private var server = MockWebServer()
    private lateinit var chat: Chat

    @Before
    fun setup() {
        server.start(8080)
        val baseUrl: HttpUrl = server.url("/v1/")
        chat = Chat(baseUrl)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `300 error`() {
        server.enqueue(MockResponse().setResponseCode(300))

        chat.load()

        assertEquals("Redirection", chat.messages())
    }

    @Test
    fun `400 error`() {
        server.enqueue(MockResponse().setResponseCode(400))

        chat.load()

        assertEquals("Client Error", chat.messages())
    }

    @Test
    fun `500 error`() {
        server.enqueue(MockResponse().setResponseCode(500))

        chat.load()

        assertEquals("Server Error", chat.messages())
    }

    @Test
    fun `Success example`() {
        server.enqueue(MockResponse().setBody("success"))

        chat.load()

        assertEquals("success", chat.messages())
        val request = server.takeRequest()
        assertEquals("POST /v1/chat/send HTTP/1.1", request.requestLine)
        assertEquals("application/json; charset=utf-8", request.getHeader("Content-Type"))
        assertEquals("{}", request.body.readUtf8())
    }

    @Test
    fun `Track header`() {
        val response = MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Cache-Control", "no-cache")
            .setBody("{}")
        server.enqueue(response)

        chat.load()

        assertEquals("{}", chat.messages())
        assertEquals("application/json; charset=utf-8", chat.headers["Content-Type"])
        assertEquals("no-cache", chat.headers["Cache-Control"])
    }

    @Test(expected = SocketTimeoutException::class)
    fun `Timeout example`() {
        val response = MockResponse()
            .addHeader("Content-Type", "application/json; charset=utf-8")
            .addHeader("Cache-Control", "no-cache")
            .setBody("{}")
            .throttleBody(1, 2, TimeUnit.SECONDS)

        server.enqueue(response)
        server.enqueue(MockResponse().setBody("success"))

        chat.load()
    }

    @Test
    fun `Dispatcher example`() {
        val dispatcher: Dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                when (request.path) {
                    "/v1/login/auth" -> return MockResponse().setResponseCode(204)
                    "/v1/check/version" -> return MockResponse().setResponseCode(200)
                        .setBody("version=9")
                    "/v1/profile/info" -> return MockResponse().setResponseCode(200)
                        .setBody("{\\\"info\\\":{\\\"name\":\"Lucas Albuquerque\",\"age\":\"21\",\"gender\":\"male\"}}")
                    "/v1/file/json" -> return MockResponse().setResponseCode(200)
                        .setBody(FileUtil.readFileWithoutNewLineFromResources("sample.json"))
                    "/v1/file/jsonverbatim" -> return MockResponse().setResponseCode(200)
                        .setBody(FileUtil.kotlinReadFileWithNewLineFromResources("sample.json"))
                }
                return MockResponse().setResponseCode(404)
            }
        }
        server.dispatcher = dispatcher

        val result1 = chat.loadPath("login", "auth")
        assertEquals(204, result1.first)
        assertEquals("", result1.second)

        val result2 = chat.loadPath("check", "version")
        assertEquals(200, result2.first)
        assertEquals("version=9", result2.second)

        val result3 = chat.loadPath("profile", "info")
        assertEquals(200, result3.first)
        assertEquals(
            "{\\\"info\\\":" +
                    "{\\\"name\":\"Lucas Albuquerque\"," +
                    "\"age\":\"21\",\"gender\":\"male\"}}", result3.second
        )

        val result4 = chat.loadPath("login", "something")
        assertEquals(404, result4.first)
        assertNull(result4.second)

        val result5 = chat.loadPath("file", "json")
        assertEquals(200, result5.first)
        assertEquals(
            "{  \"testing\": \"result\",  \"array\": [    " +
                    "{\"first\":\"good\"},    {\"second\":\"bad\"}  ]}", result5.second
        )

        val result6 = chat.loadPath("file", "jsonverbatim")
        assertEquals(200, result6.first)
        assertEquals(
            "{\n  \"testing\": \"result\",\n  \"array\": [\n    " +
                    "{\"first\":\"good\"},\n    {\"second\":\"bad\"}\n  ]\n}", result6.second
        )
    }

    @Test
    fun `test file reading`() {
        val finalResult = "{\n  \"testing\": \"result\",\n  \"array\": [\n    " +
                "{\"first\":\"good\"},\n    {\"second\":\"bad\"}\n  ]\n}"

        assertEquals(finalResult,
            FileUtil.readFileWithNewLineFromResources("sample.json")
        )

        assertEquals(finalResult,
            FileUtil.kotlinReadFileWithNewLineFromResources("sample.json")
        )

        assertEquals(finalResult,
            String(FileUtil.readBinaryFileFromResources("sample.json"))
        )

        assertEquals(finalResult,
            String(FileUtil.kotlinReadBinaryFileFromResources("sample.json"))
        )
    }

    @Test
    fun `load more`() {
        // Schedule some responses.
        server.enqueue(MockResponse().setBody("hello, world!"))
        server.enqueue(MockResponse().setBody("sup, bra?"))
        server.enqueue(MockResponse().setBody("yo dog"))

        chat.loadMore()
        assertEquals("hello, world!", chat.messages())
        chat.loadMore()
        chat.loadMore()
        assertEquals(
            """
    hello, world!
    sup, bra?
    yo dog
    """.trimIndent(), chat.messages()
        )

        // Optional: confirm that your app made the HTTP requests you were expecting.
        val request1: RecordedRequest = server.takeRequest()
        assertEquals("/v1/chat/messages/", request1.path)
        assertNotNull(request1.getHeader("Authorization"))
        val request2: RecordedRequest = server.takeRequest()
        assertEquals("/v1/chat/messages/2", request2.path)
        val request3: RecordedRequest = server.takeRequest()
        assertEquals("/v1/chat/messages/3", request3.path)

    }

    object FileUtil {
        @Throws(IOException::class)
        fun readFileWithoutNewLineFromResources(fileName: String): String {
            var inputStream: InputStream? = null
            try {
                inputStream = getInputStreamFromResource(fileName)
                val builder = StringBuilder()
                val reader = BufferedReader(InputStreamReader(inputStream))

                var str: String? = reader.readLine()
                while (str != null) {
                    builder.append(str)
                    str = reader.readLine()
                }
                return builder.toString()
            } finally {
                inputStream?.close()
            }
        }

        @Throws(IOException::class)
        fun readFileWithNewLineFromResources(fileName: String): String {
            var inputStream: InputStream? = null
            try {
                inputStream = getInputStreamFromResource(fileName)
                val builder = StringBuilder()
                val reader = BufferedReader(InputStreamReader(inputStream))

                var theCharNum = reader.read()
                while (theCharNum != -1) {
                    builder.append(theCharNum.toChar())
                    theCharNum = reader.read()
                }

                return builder.toString()
            } finally {
                inputStream?.close()
            }
        }

        fun kotlinReadFileWithNewLineFromResources(fileName: String): String {
            return getInputStreamFromResource(fileName)?.bufferedReader()
                .use { bufferReader -> bufferReader?.readText() } ?: ""
        }

        @Throws(IOException::class)
        fun readBinaryFileFromResources(fileName: String): ByteArray {
            var inputStream: InputStream? = null
            val byteStream = ByteArrayOutputStream()
            try {
                inputStream = getInputStreamFromResource(fileName)

                var nextValue = inputStream?.read() ?: -1

                while (nextValue != -1) {
                    byteStream.write(nextValue)
                    nextValue = inputStream?.read() ?: -1
                }
                return byteStream.toByteArray()

            } finally {
                inputStream?.close()
                byteStream.close()
            }
        }

        fun kotlinReadBinaryFileFromResources(fileName: String): ByteArray {
            ByteArrayOutputStream().use { byteStream ->
                getInputStreamFromResource(fileName)?.copyTo(byteStream)
                return byteStream.toByteArray()
            }
        }

        private fun getInputStreamFromResource(fileName: String)
                = javaClass.classLoader?.getResourceAsStream(fileName)
    }
}
