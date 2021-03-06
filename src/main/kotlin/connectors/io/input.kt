package connectors.io


import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import configuration.Config
import connectors.*
import javafx.scene.image.Image
import job.JobConnectorData
import java.io.InputStream
import java.nio.file.*


typealias InputStreamConsumer = (InputStream) -> Unit

data class InputRecord(val type : String,
                       val root : String,
                  val folder : String,
                  val objectName : String,
                  val consume : (inputFunction : InputStreamConsumer) -> Unit)

val localFileConfigDescription = ConfigDescription(
    ComposedType(
        Fields.Builder()
            .add("root", StringType())
            .add("pattern", StringType())
            .add("subFolder", BooleanType())
            .build()
    )
)

object LocalFileDescriptor :
        ConnectorDesc(
            VersionedIdentifier("Local File Input", Version(listOf(1))),
            Link(arrayOf()),
            InputRecord::class,
            localFileConfigDescription,
            { Image("file:" +  Thread.currentThread().contextClassLoader.getResource("./iconFiles.png").path) },
            { j: JobConnectorData,  c : Config -> LocalFileConnector(c) })
         {
            init {
                Connectors.register(this)
            }
        }

class LocalFileConnector(config : Config) : Connector(config) {
    override fun run(input: Any?, output: (Any?) -> Unit) {
        val root = config.get("root") ?: ""
        val rootPath = Path.of(root)
        val pattern = config.get("pattern") ?: ""
        val sub : Boolean = config.get("subFolder")?.toBoolean() ?: false

        // create a matcher
        val fs: FileSystem = rootPath.getFileSystem()
        val matcher: PathMatcher = fs.getPathMatcher("glob:$pattern")

        this.exploreDirectory(rootPath, root, matcher, sub, output)
    }

    private fun exploreDirectory(path: Path, root: String, matcher: PathMatcher, sub: Boolean, output: (Any?) -> Unit) {
        val dirStream = Files.newDirectoryStream(path)
        dirStream.forEach {
            this.explore(it, root, matcher, sub, output)
        }
    }

    private fun explore(path : Path, root : String, matcher: PathMatcher, sub:Boolean, output: (Any?) -> Unit) {
        if (path.toFile().isFile) {
            if (matcher.matches(path.fileName)) {
                val record = InputRecord(
                    "localIO",
                    root ?: "",
                    path.parent.toFile().path.substring((root ?: "").length),
                    path.toFile().name
                )
                { consumer: InputStreamConsumer ->
                    Files.newInputStream(path).use(consumer)
                }
                output(record)
            }
        }
        else if (path.toFile().isDirectory && sub) {
            this.exploreDirectory(path, root, matcher, sub, output)
        }
    }
}

class S3Input(config: Config) : Connector(config) {
    
    override fun run(input: Any?, output: (Any?) -> Unit) {
        val keyName = config.get("key") ?: ""
        val bucketName = config.get("bucket") ?: ""
        val request =  GetObjectRequest(bucketName, keyName)

        val credentials: Config? = config.sub["credentials"]
        val accessKey = credentials?.get("accessKey") ?: ""
        val secretKey = credentials?.get("accessKey") ?: ""

        val awsCreds = BasicAWSCredentials(accessKey, secretKey)

        val region = config.get("region") ?: ""
        val s3Client = AmazonS3ClientBuilder.standard()
            .withCredentials(AWSStaticCredentialsProvider(awsCreds))
            .withRegion(region).build()

        val record = InputRecord(
            "S3",
            bucketName,
            bucketName,
            bucketName)
        { consumer: InputStreamConsumer ->
            val s3Object = s3Client.getObject(request)
            val objectData: InputStream = s3Object.objectContent
            objectData.use(consumer)
        }
        output(record)
    }

}
