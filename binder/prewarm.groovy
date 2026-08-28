// Pre-warms the Grape cache at Binder image-build time for the headline
// notebooks (whiskey/iris use Smile 1.5.3, the last Apache-2.0 release).
@Grab('com.github.haifengl:smile-core:1.5.3')
import smile.clustering.KMeans

println "Grape cache warmed: ${KMeans.name} resolvable"
