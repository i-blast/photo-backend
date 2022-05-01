package com.spring.kotlin

import com.google.cloud.spring.data.datastore.core.mapping.Entity
import com.google.cloud.spring.data.datastore.repository.DatastoreRepository
import com.google.cloud.spring.vision.CloudVisionTemplate
import com.google.cloud.vision.v1.Feature
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.core.io.Resource
import org.springframework.core.io.WritableResource
import org.springframework.data.annotation.Id
import org.springframework.data.rest.core.annotation.RepositoryRestResource
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.util.*

@SpringBootApplication
class PhotoBackendApplication

fun main(args: Array<String>) {
    runApplication<PhotoBackendApplication>(*args)
}

@RestController
class HelloController(
    private val photoRepository: PhotoRepository
) {

    @GetMapping("/")
    fun hello() = "Hello!"

    @PostMapping("/photo")
    fun create(@RequestBody photo: Photo) {
        photoRepository.save(photo)
    }
}

@Entity
data class Photo(
    @Id
    var id: String? = null,
    var uri: String? = null,
    var label: String? = null
)

@RepositoryRestResource
interface PhotoRepository : DatastoreRepository<Photo, String>

@RestController
class UploadController(
    private val photoRepository: PhotoRepository,
    private val ctx: ApplicationContext,
    private val visionTemplate: CloudVisionTemplate
) {

    private val logger = LoggerFactory.getLogger(this.javaClass)

    private val bucket = "gs://photo-backend-app-photos/images"

    @PostMapping("/upload")
    fun upload(@RequestParam("file") file: MultipartFile): Photo {
        val id = UUID.randomUUID().toString()
        val uri = "$bucket/$id"

        val gcs = ctx.getResource(uri) as WritableResource

        file.inputStream.use { input ->
            gcs.outputStream.use { output ->
                input.copyTo(output)
            }
        }

        val response = visionTemplate.analyzeImage(file.resource, Feature.Type.LABEL_DETECTION)
        logger.info(response.toString())
        val labels = response.labelAnnotationsList.sortedByDescending { it.score }
            .take(10)
            .joinToString(" ") { it.description }

        return photoRepository.save(
            Photo(
                id = id,
                uri = "/images/$id",
                label = labels
            )
        )
    }

    @GetMapping("/image/{id}")
    fun get(@PathVariable id: String): ResponseEntity<Resource> {
        val resource = ctx.getResource("$bucket/$id")

        return if (resource.exists()) {
            ResponseEntity.ok(resource)
        } else {
            ResponseEntity(HttpStatus.NOT_FOUND)
        }
    }
}
