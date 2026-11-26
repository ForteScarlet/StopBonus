package update

import config.BuildConfig
import org.apache.maven.artifact.versioning.ComparableVersion
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Properties
import java.util.concurrent.Flow

internal data class UpdateVersion(val number: String, val revision: Int?) {
    private val comparable = ComparableVersion(number)

    val label: String
        get() = if (revision != null && revision > 0) "v$number（修订 $revision）" else "v$number"

    fun isNewerThan(current: UpdateVersion): Boolean {
        val comparison = comparable.compareTo(current.comparable)
        return comparison > 0 || comparison == 0 &&
            revision != null && current.revision != null && revision > current.revision
    }

    companion object {
        private val releaseNumber = Regex("[0-9]+(?:\\.[0-9]+)+(?:[-+][0-9A-Za-z.-]+)?")

        fun from(number: String?, revision: String?): UpdateVersion {
            require(number != null && number.length <= 96 && releaseNumber.matches(number)) { "Invalid update version" }
            val packageRevision = revision?.let {
                requireNotNull(it.toIntOrNull()?.takeIf { value -> value >= 0 }) { "Invalid update revision" }
            }
            return UpdateVersion(number, packageRevision)
        }
    }
}

internal class UpdateRepository {
    val current: UpdateVersion = UpdateVersion(
        System.getProperty("app.version") ?: BuildConfig.VERSION,
        System.getProperty("app.revision")?.toIntOrNull(),
    )

    private val client by lazy { HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build() }

    fun latest(): UpdateVersion {
        val site = System.getProperty("app.repositoryUrl") ?: BuildConfig.UPDATE_SITE_URL
        val endpoint = URI.create(site.trimEnd('/') + "/metadata.properties")
        require(endpoint.scheme == "https" && endpoint.host != null && endpoint.userInfo == null) {
            "Invalid update site"
        }
        val request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(8))
            .header("User-Agent", "StopBonus/${BuildConfig.VERSION}")
            .GET()
            .build()
        val response = client.send(request) { limitedResponseBody() }
        if (response.statusCode() != 200) throw IOException("Update site returned HTTP ${response.statusCode()}")
        val properties = Properties().apply { response.body().inputStream().use(::load) }
        return UpdateVersion.from(properties.getProperty("app.version"), properties.getProperty("app.revision"))
    }

    private fun limitedResponseBody(): HttpResponse.BodySubscriber<ByteArray> {
        val body = HttpResponse.BodySubscribers.ofByteArray()
        return object : HttpResponse.BodySubscriber<ByteArray> by body {
            private lateinit var subscription: Flow.Subscription
            private var remaining = 65_536L

            override fun onSubscribe(subscription: Flow.Subscription) {
                this.subscription = subscription
                body.onSubscribe(subscription)
            }

            override fun onNext(items: List<ByteBuffer>) {
                remaining -= items.sumOf { it.remaining().toLong() }
                if (remaining < 0) {
                    subscription.cancel()
                    body.onError(IOException("Update metadata exceeds the size limit"))
                } else {
                    body.onNext(items)
                }
            }
        }
    }
}
