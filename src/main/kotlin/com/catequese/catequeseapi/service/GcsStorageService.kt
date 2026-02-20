import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.util.*

@Service
class GcsStorageService(
    @Value("\${gcs.bucket}") private val bucketName: String
) {
    private val storage: Storage = run {
        val json = System.getenv("GOOGLE_APPLICATION_CREDENTIALS_JSON")
        if (!json.isNullOrBlank()) {
            val creds = GoogleCredentials.fromStream(ByteArrayInputStream(json.toByteArray()))
            StorageOptions.newBuilder().setCredentials(creds).build().service
        } else {
            StorageOptions.getDefaultInstance().service
        }
    }

    fun upload(file: MultipartFile): String {
        val safeName = file.originalFilename?.replace("\\s+".toRegex(), "_") ?: "file"
        val objectName = "uploads/${UUID.randomUUID()}-$safeName"

        val blobInfo = BlobInfo.newBuilder(bucketName, objectName)
            .setContentType(file.contentType ?: "application/octet-stream")
            .build()

        storage.create(blobInfo, file.bytes)

        // Retorne o objectName (melhor) ou a URL (se for usar URL assinada depois)
        return objectName
    }
}