package com.sky.wallapp

data class Model(
    var title: String? = null,
    var image: String? = null,
    var search: String? = null,
    var thumbs: String? = null,
    var cloudinaryUrl: String? = null
)

/** URL to load for display: the Cloudinary backup when present (avoids 402 quota errors), else the primary image. */
val Model.displayUrl: String?
    get() = cloudinaryUrl?.takeIf { it.isNotBlank() } ?: image
