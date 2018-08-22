package com.exasol.spark.util

import java.sql.ResultSet
import java.sql.ResultSetMetaData

import org.apache.spark.sql.types._

import com.typesafe.scalalogging.LazyLogging

/** A helper class with mapping functions from Exasol JDBC types to/from Spark SQL types */
object Types extends LazyLogging {

  val LongDecimal: DecimalType = DecimalType(20, 0) // scalastyle:ignore magic.number

  /**
   * Given a [[java.sql.ResultSetMetaData]] returns a Spark
   * [[org.apache.spark.sql.types.StructType]] schema
   *
   * @param rsmd A result set metadata
   * @return A StructType matching result set types
   */
  def createSparkStructType(rsmd: ResultSetMetaData): StructType = {
    val columnCnt = rsmd.getColumnCount
    val fields = new Array[StructField](columnCnt)
    var idx = 0
    while (idx < columnCnt) {
      val columnName = rsmd.getColumnLabel(idx + 1)
      val columnDataType = rsmd.getColumnType(idx + 1)
      val columnPrecision = rsmd.getPrecision(idx + 1)
      val columnScale = rsmd.getScale(idx + 1)
      val isSigned = rsmd.isSigned(idx + 1)
      val isNullable = rsmd.isNullable(idx + 1) != ResultSetMetaData.columnNoNulls

      val columnType =
        createSparkTypeFromSQLType(columnDataType, columnPrecision, columnScale, isSigned)

      fields(idx) = StructField(columnName, columnType, isNullable)
      idx += 1
    }
    new StructType(fields)
  }

  /**
   * Maps a JDBC type [[java.sql.Types$]] to a Spark SQL [[org.apache.spark.sql.types.DataType]]
   *
   * @param sqlType A JDBC type from [[java.sql.ResultSetMetaData]] column type
   * @param precision A precision value obtained from ResultSetMetaData, rsmd.getPrecision(index)
   * @param scale A scale value obtained from ResultSetMetaData, rsmd.getScale(index)
   * @param isSigned A isSigned value obtained from ResultSetMetaData, rsmd.isSigned(index)
   * @return A Spark SQL DataType corresponding to JDBC SQL type
   */
  def createSparkTypeFromSQLType(
    sqlType: Int,
    precision: Int,
    scale: Int,
    isSigned: Boolean
  ): DataType = sqlType match {
    // Numbers
    case java.sql.Types.TINYINT  => ShortType
    case java.sql.Types.SMALLINT => ShortType
    case java.sql.Types.INTEGER =>
      if (isSigned) {
        IntegerType
      } else {
        LongType
      }
    case java.sql.Types.BIGINT =>
      if (isSigned) {
        LongType
      } else {
        LongDecimal
      }
    case java.sql.Types.DECIMAL =>
      if (precision != 0 || scale != 0) {
        boundedDecimal(precision, scale)
      } else {
        DecimalType.SYSTEM_DEFAULT
      }
    case java.sql.Types.NUMERIC =>
      if (precision != 0 || scale != 0) {
        boundedDecimal(precision, scale)
      } else {
        DecimalType.SYSTEM_DEFAULT
      }
    case java.sql.Types.DOUBLE => DoubleType
    case java.sql.Types.FLOAT  => DoubleType
    case java.sql.Types.REAL   => FloatType

    // Stings
    case java.sql.Types.CHAR         => StringType
    case java.sql.Types.NCHAR        => StringType
    case java.sql.Types.VARCHAR      => StringType
    case java.sql.Types.NVARCHAR     => StringType
    case java.sql.Types.LONGVARCHAR  => StringType
    case java.sql.Types.LONGNVARCHAR => StringType

    // Binaries
    case java.sql.Types.BINARY        => BinaryType
    case java.sql.Types.VARBINARY     => BinaryType
    case java.sql.Types.LONGVARBINARY => BinaryType

    // Booleans
    case java.sql.Types.BIT     => BooleanType
    case java.sql.Types.BOOLEAN => BooleanType

    // Datetime
    case java.sql.Types.DATE      => DateType
    case java.sql.Types.TIME      => TimestampType
    case java.sql.Types.TIMESTAMP => TimestampType

    // Others
    case java.sql.Types.ROWID  => LongType
    case java.sql.Types.STRUCT => StringType
    case _ =>
      throw new IllegalArgumentException(s"Received an unsupported SQL type $sqlType")
  }

  /**
   * Bound DecimalType within Spark [[DecimalType.MAX_PRECISION]] and [[DecimalType.MAX_SCALE]]
   * values
   */
  private[this] def boundedDecimal(precision: Int, scale: Int): DecimalType =
    DecimalType(
      math.min(precision, DecimalType.MAX_PRECISION),
      math.min(scale, DecimalType.MAX_SCALE)
    )

  /**
   * Select only required columns from Spark SQL schema
   *
   * Adapted from Spark JDBCRDD private function `pruneSchema`.
   *
   * @param schema A Spark SQL schema
   * @param columns A list of required columns
   * @return A Spark SQL schema with only columns in the given order
   */
  def selectColumns(columns: Array[String], schema: StructType): StructType = {
    val columnNames = columns.toSet
    val newFields = schema.fields.filter(f => columnNames.contains(f.name))
    logger.debug(s"A new pruned columns obtained ${newFields.mkString(",")}")
    StructType(newFields)
  }

}