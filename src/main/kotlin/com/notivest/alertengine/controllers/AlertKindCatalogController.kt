package com.notivest.alertengine.controllers

import com.notivest.alertengine.catalog.AlertKindCatalog
import com.notivest.alertengine.controllers.dto.alertkind.AlertKindCatalogResponse
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/alert-kinds")
class AlertKindCatalogController(
    private val catalog: AlertKindCatalog,
) {

    @GetMapping
    fun list(): ResponseEntity<AlertKindCatalogResponse> {
        val response = catalog.getCatalog()
        return ResponseEntity.ok()
            .eTag(response.version)
            .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
            .body(response)
    }
}
