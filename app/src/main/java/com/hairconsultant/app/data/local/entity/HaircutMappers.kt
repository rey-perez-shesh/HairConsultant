package com.hairconsultant.app.data.local.entity

import com.hairconsultant.app.domain.model.ArType
import com.hairconsultant.app.domain.model.FaceShape
import com.hairconsultant.app.domain.model.GlbFit
import com.hairconsultant.app.domain.model.HairLength
import com.hairconsultant.app.domain.model.HairTexture
import com.hairconsultant.app.domain.model.Haircut
import com.hairconsultant.app.domain.model.HaircutGenderStyle
import com.hairconsultant.app.domain.model.TreatmentPreference

/** The one Room <-> domain mapping for catalog rows, shared by every repository that reads them. */
fun HaircutEntity.toDomain() = Haircut(
    id = id,
    name = name,
    imageUrl = imageUrl,
    length = runCatching { HairLength.valueOf(length) }.getOrDefault(HairLength.MEDIUM),
    texture = runCatching { HairTexture.valueOf(texture) }.getOrDefault(HairTexture.STRAIGHT),
    recommendedFaceShapes = recommendedFaceShapes.mapNotNull { runCatching { FaceShape.valueOf(it) }.getOrNull() },
    genderStyle = runCatching { HaircutGenderStyle.valueOf(genderStyle) }.getOrDefault(HaircutGenderStyle.UNISEX),
    treatment = runCatching { TreatmentPreference.valueOf(treatment) }.getOrDefault(TreatmentPreference.NONE),
    description = description,
    sortOrder = sortOrder,
    arType = runCatching { ArType.valueOf(arType) }.getOrDefault(ArType.AUTO),
    arAssetUrl = arAssetUrl,
    arAssetVersion = arAssetVersion,
    glbFit = GlbFit(
        scaleMul = glbScaleMul,
        offsetX = glbOffsetX,
        offsetY = glbOffsetY,
        offsetZ = glbOffsetZ,
        yawOffset = glbYawOffset
    )
)

fun Haircut.toEntity() = HaircutEntity(
    id = id,
    name = name,
    imageUrl = imageUrl,
    length = length.name,
    texture = texture.name,
    recommendedFaceShapes = recommendedFaceShapes.map { it.name },
    genderStyle = genderStyle.name,
    treatment = treatment.name,
    description = description,
    sortOrder = sortOrder,
    arType = arType.name,
    arAssetUrl = arAssetUrl,
    arAssetVersion = arAssetVersion,
    glbScaleMul = glbFit.scaleMul,
    glbOffsetX = glbFit.offsetX,
    glbOffsetY = glbFit.offsetY,
    glbOffsetZ = glbFit.offsetZ,
    glbYawOffset = glbFit.yawOffset
)
