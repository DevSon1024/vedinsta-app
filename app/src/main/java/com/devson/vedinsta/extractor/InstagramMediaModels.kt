package com.devson.vedinsta.extractor

import com.google.gson.annotations.SerializedName

/**
 * Root response for Instagram shortcode GraphQL queries.
 */
data class ShortcodeMediaResponse(
    @SerializedName("data") val data: ShortcodeMediaData?
)

/**
 * Inner data wrapper for GraphQL responses.
 */
data class ShortcodeMediaData(
    @SerializedName(value = "xdt_shortcode_media", alternate = ["shortcode_media"]) val shortcodeMedia: ShortcodeMedia?
)

/**
 * Main ShortcodeMedia model containing video, image, or sidecar carousel data.
 */
data class ShortcodeMedia(
    @SerializedName("__typename") val typename: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("shortcode") val shortcode: String?,
    @SerializedName("display_url") val displayUrl: String?,
    @SerializedName("is_video") val isVideo: Boolean?,
    @SerializedName("video_url") val videoUrl: String?,
    @SerializedName("video_versions") val videoVersions: List<VideoVersion>?,
    @SerializedName("display_resources") val displayResources: List<ImageVersion>?,
    @SerializedName("edge_sidecar_to_children") val edgeSidecarToChildren: EdgeSidecarToChildren?,
    @SerializedName("owner") val owner: Owner?,
    @SerializedName("edge_media_to_caption") val edgeMediaToCaption: EdgeMediaToCaption?
)

/**
 * Wrapper for list of sidecar edges (carousel children).
 */
data class EdgeSidecarToChildren(
    @SerializedName("edges") val edges: List<SidecarEdge>?
)

/**
 * Individual sidecar edge.
 */
data class SidecarEdge(
    @SerializedName("node") val node: SidecarNode?
)

/**
 * Individual media node within a sidecar carousel.
 */
data class SidecarNode(
    @SerializedName("__typename") val typename: String?,
    @SerializedName("id") val id: String?,
    @SerializedName("shortcode") val shortcode: String?,
    @SerializedName("display_url") val displayUrl: String?,
    @SerializedName("is_video") val isVideo: Boolean?,
    @SerializedName("video_url") val videoUrl: String?,
    @SerializedName("video_versions") val videoVersions: List<VideoVersion>?,
    @SerializedName("display_resources") val displayResources: List<ImageVersion>?
)

/**
 * Model representing a video file version.
 */
data class VideoVersion(
    @SerializedName("url") val url: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

/**
 * Model representing an image file version.
 */
data class ImageVersion(
    @SerializedName(value = "src", alternate = ["url"]) val url: String?,
    @SerializedName("width") val width: Int?,
    @SerializedName("height") val height: Int?
)

/**
 * Legacy API response model.
 * Handles the items structure returned by older endpoints.
 */
data class LegacyMediaResponse(
    @SerializedName("items") val items: List<LegacyMediaItem>?
)

/**
 * Item in the legacy media API response.
 */
data class LegacyMediaItem(
    @SerializedName("code") val code: String?,
    @SerializedName("pk") val pk: String?,
    @SerializedName("media_type") val mediaType: Int?,
    @SerializedName("image_versions2") val imageVersions2: LegacyImageVersions2?,
    @SerializedName("video_versions") val videoVersions: List<VideoVersion>?,
    @SerializedName("carousel_media") val carouselMedia: List<LegacyCarouselMedia>?,
    @SerializedName("user") val user: LegacyUser?,
    @SerializedName("caption") val caption: LegacyCaption?
)

/**
 * Wrapper for image candidate lists in legacy responses.
 */
data class LegacyImageVersions2(
    @SerializedName("candidates") val candidates: List<ImageVersion>?
)

/**
 * Legacy sidecar carousel media node.
 */
data class LegacyCarouselMedia(
    @SerializedName("id") val id: String?,
    @SerializedName("media_type") val mediaType: Int?,
    @SerializedName("image_versions2") val imageVersions2: LegacyImageVersions2?,
    @SerializedName("video_versions") val videoVersions: List<VideoVersion>?
)

data class Owner(
    @SerializedName("username") val username: String?
)

data class EdgeMediaToCaption(
    @SerializedName("edges") val edges: List<CaptionEdge>?
)

data class CaptionEdge(
    @SerializedName("node") val node: CaptionNode?
)

data class CaptionNode(
    @SerializedName("text") val text: String?
)

data class LegacyUser(
    @SerializedName("username") val username: String?
)

data class LegacyCaption(
    @SerializedName("text") val text: String?
)

/**
 * Root response for PolarisPostRootQuery GraphQL query.
 */
data class PolarisPostRootResponse(
    @SerializedName("data") val data: PolarisPostRootData?
)

data class PolarisPostRootData(
    @SerializedName("xdt_api__v1__media__shortcode__web_info") val webInfo: PolarisPostWebInfo?
)

data class PolarisPostWebInfo(
    @SerializedName("items") val items: List<LegacyMediaItem>?
)

