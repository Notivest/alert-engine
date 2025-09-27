package com.notivest.alert_engine.models.converters

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.Duration

@Converter(autoApply = false)
class DurationSecondsConverter : AttributeConverter<Duration, Long> {
    override fun convertToDatabaseColumn(attribute: Duration?): Long? {
        return attribute?.seconds
    }

    override fun convertToEntityAttribute(dbData: Long?): Duration? {
        return dbData?.let { Duration.ofSeconds(it) }
    }

}