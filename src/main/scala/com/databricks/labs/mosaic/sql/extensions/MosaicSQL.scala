package com.databricks.labs.mosaic.sql.extensions

import com.databricks.labs.mosaic._
import com.databricks.labs.mosaic.core.geometry.api.{ESRI, JTS}
import com.databricks.labs.mosaic.core.index.{BNGIndexSystem, H3IndexSystem, IndexSystemFactory}
import com.databricks.labs.mosaic.core.raster.api.RasterAPI.GDAL
import com.databricks.labs.mosaic.functions.MosaicContext
import org.apache.spark.internal.Logging
import org.apache.spark.sql.SparkSessionExtensions

/**
  * Supports automatic registration of SQL expressions at the cluster start up
  * time. This class registers and injects a rule that based on the configured
  * index system and configured geometry api adds corresponding SQL expressions
  * to spark session. These rules are activated just after the spark session has
  * been created. If unsupported pair of index system and geometry api has been
  * provided this operation will crash the code in order to make sure correct
  * functionality has been enabled.
  */
class MosaicSQL extends (SparkSessionExtensions => Unit) with Logging {

    /**
      * Constructor for the MosaicSQL extension. All the registration logic
      * happens before a nop rule has been injected.
      * @param ext
      *   Hook to spark session that is needed for expression registration.
      */
    override def apply(ext: SparkSessionExtensions): Unit = {
        ext.injectCheckRule(spark => {
            var indexSystem: String = spark.conf.get(MOSAIC_INDEX_SYSTEM)
            var CustomIndexSystem = IndexSystemFactory.getIndexSystem("CUSTOM(-180,180,-90,90,2,30,30)") // Placeholder to register var

            // If custom, build using indexFactory
            if(indexSystem.length > 5 && indexSystem.substring(0, 6) == "CUSTOM"){
                CustomIndexSystem = IndexSystemFactory.getIndexSystem(indexSystem)
                indexSystem = "CUSTOM"
            }

            val geometryAPI = spark.conf.get(MOSAIC_GEOMETRY_API)
            // val rasterAPI = spark.conf.get(MOSAIC_RASTER_API)
            val rasterAPI = "GDAL"
            val mosaicContext = (indexSystem, geometryAPI, rasterAPI) match {
                case ("CUSTOM", "JTS", "GDAL") => MosaicContext.build(CustomIndexSystem, JTS, GDAL)
                case ("CUSTOM", "ESRI", "GDAL") => MosaicContext.build(CustomIndexSystem, ESRI, GDAL)
                case ("H3", "JTS", "GDAL")   => MosaicContext.build(H3IndexSystem, JTS, GDAL)
                case ("H3", "ESRI", "GDAL")  => MosaicContext.build(H3IndexSystem, ESRI, GDAL)
                case ("BNG", "JTS", "GDAL")  => MosaicContext.build(BNGIndexSystem, JTS, GDAL)
                case ("BNG", "ESRI", "GDAL") => MosaicContext.build(BNGIndexSystem, ESRI, GDAL)
                case (is, gapi, rapi) => throw new Error(s"Index system, geometry API and rasterAPI: ($is, $gapi, $rapi) not supported.")
            }
            logInfo(s"Registering Mosaic SQL Extensions ($indexSystem, $geometryAPI, $rasterAPI).")
            mosaicContext.register(spark)
            // NOP rule. This rule is specified only to respect syntax.
            _ => ()
        })
    }

}
