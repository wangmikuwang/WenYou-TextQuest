# WenYou TextQuest keeps default ProGuard rules for the app module.
# kotlinx.serialization models are reflected by the generated serializers and
# do not need extra keep rules as long as R8 keeps @Serializable metadata
# (default for library consumers of the serialization runtime).
