package connectors.io


import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import configuration.Config
import connectors.*
import connectors.commons.RowError
import functions.InputItem
import functions.OutputFunction
import job.JobConnectorData
import java.io.IOException
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
            LinkInput(arrayOf()),
            LinkOutput().add("main", InputRecord::class)
                .add("error", RowError::class),
            localFileConfigDescription,
            findImage("iconFiles.png"),
            { j: JobConnectorData,  c : Config -> LocalFileConnector(c) })
         {
            init {
                Connectors.register(this)
            }
        }

class LocalFileConnector(config : Config) : Connector(config) {
    override fun run(input: InputItem, output: OutputFunction) {
        val root = config.get("root") ?: ""
        val rootPath = Path.of(root)
        val pattern = config.get("pattern") ?: ""
        val sub : Boolean = config.get("subFolder")?.toBoolean() ?: false

        // create a matcher
        val fs: FileSystem = rootPath.getFileSystem()
        val matcher: PathMatcher = fs.getPathMatcher("glob:$pattern")

        this.exploreDirectory(rootPath, root, matcher, sub, output)
    }

    private fun exploreDirectory(path: Path, root: String, matcher: PathMatcher, sub: Boolean, output: OutputFunction) {
        try {
            val dirStream = Files.newDirectoryStream(path)
            dirStream.forEach {
                this.explore(it, root, matcher, sub, output)
            }
        }
        catch (ex: IOException) {
            output("error", RowError(this, "Can't explore ${path.toFile().name}", ex))
        }
    }

    private fun explore(path : Path, root : String, matcher: PathMatcher, sub:Boolean, output: OutputFunction) {
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
                output("main", record)
            }
        }
        else if (path.toFile().isDirectory && sub) {
            this.exploreDirectory(path, root, matcher, sub, output)
        }
    }
}

val S3ConfigDescription = ConfigDescription(
    ComposedType(Fields.Builder()
            .add("key", StringType())
            .add("bucket", StringType())
            .add("credentials",
                ComposedType(
                    Fields.Builder()
                        .add("accessKey", StringType())
                        .add("secretKey", StringType())
                        .build()
                ))
            .build()
    )
)

object S3Descriptor :
    ConnectorDesc(
        VersionedIdentifier("S3 File Input", Version(listOf(1))),
        LinkInput(arrayOf()),
        LinkOutput().add("main", InputRecord::class)
            .add("error", RowError::class),
        S3ConfigDescription,
        findImage("iconS3.png"),
        { j: JobConnectorData,  c : Config -> S3Input(c) })
{
    init {
        Connectors.register(this)
    }
}

class S3Input(config: Config) : Connector(config) {
    
    override fun run(input: InputItem, output: OutputFunction) {
        val keyName = config.get("key") ?: ""
        val bucketName = config.get("bucket") ?: ""
        val request =  GetObjectRequest(bucketName, keyName)

        val credentials: Config? = config.sub["credentials"]
        val accessKey = credentials?.get("accessKey") ?: ""
        val secretKey = credentials?.get("secretKey") ?: ""

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
        output("main", record)
    }

}
