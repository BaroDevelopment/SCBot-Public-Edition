
import net.dv8tion.jda.api.utils.data.DataObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.nio.charset.StandardCharsets

// THIS CODE IS FROM https://github.com/Xirado/Bean

object Hastebin {
    private const val BASE = "https://hastebin.de"


    fun post(text: String, raw: Boolean, extension: String? = null): String {
        val data = text.toByteArray(StandardCharsets.UTF_8)
        val length = data.size
        val body = RequestBody.create(null, data)
        val request = Request.Builder()
            .url("$BASE/documents")
            .post(body)
            .header("User-Agent", "SCBot Discord Bot (https://github.com/astatio/scbot)")
            .header("Content-Length", length.toString())
            .build()
        val response: Response = OkHttpClient().newCall(request).execute()
        val `object` = DataObject.fromJson(response.body!!.byteStream())
        return if (raw) BASE + "/raw/" + `object`.getString("key") else BASE + "/" + `object`.getString("key") + if (extension == null) "" else ".$extension"
    }
}