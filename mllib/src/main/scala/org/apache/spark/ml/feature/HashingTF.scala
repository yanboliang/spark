/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.ml.feature

import org.apache.spark.annotation.{Experimental, Since}
import org.apache.spark.ml.Transformer
import org.apache.spark.ml.attribute.AttributeGroup
import org.apache.spark.ml.param._
import org.apache.spark.ml.param.shared.{HasInputCol, HasOutputCol}
import org.apache.spark.ml.util._
import org.apache.spark.mllib.feature
import org.apache.spark.sql.{DataFrame, Dataset}
import org.apache.spark.sql.functions.{col, udf}
import org.apache.spark.sql.types.{ArrayType, StructType}

/**
 * :: Experimental ::
 * Maps a sequence of terms to their term frequencies using the hashing trick.
 */
@Experimental
class HashingTF(override val uid: String)
  extends Transformer with HasInputCol with HasOutputCol with DefaultParamsWritable {

  def this() = this(Identifiable.randomUID("hashingTF"))

  /** @group setParam */
  def setInputCol(value: String): this.type = set(inputCol, value)

  /** @group setParam */
  def setOutputCol(value: String): this.type = set(outputCol, value)

  /**
   * Number of features.  Should be > 0.
   * (default = 2^18^)
   * @group param
   */
  val numFeatures = new IntParam(this, "numFeatures", "number of features (> 0)",
    ParamValidators.gt(0))

  /**
   * Binary toggle to control term frequency counts.
   * If true, all non-zero counts are set to 1.  This is useful for discrete probabilistic
   * models that model binary events rather than integer counts.
   * (default = false)
   * @group param
   */
  val binary = new BooleanParam(this, "binary", "If true, all non zero counts are set to 1. " +
    "This is useful for discrete probabilistic models that model binary events rather " +
    "than integer counts")

  /**
    * The hash algorithm used when mapping term to integer.
    * Supported options: "murmur3" and "native".
    * (Default = "murmur3")
    * @group param
    */
  val hashAlgorithm = new Param[String](this, "hashAlgorithm", "The hash algorithm used when " +
    "mapping term to integer. Supported options: murmur3(default) and native.",
    ParamValidators.inArray[String](feature.HashingTF.supportedHashAlgorithms.toArray))

  setDefault(numFeatures -> (1 << 18), binary -> false,
    hashAlgorithm -> feature.HashingTF.Murmur3)

  /** @group getParam */
  def getNumFeatures: Int = $(numFeatures)

  /** @group setParam */
  def setNumFeatures(value: Int): this.type = set(numFeatures, value)

  /** @group getParam */
  def getBinary: Boolean = $(binary)

  /** @group setParam */
  def setBinary(value: Boolean): this.type = set(binary, value)

  /** @group getParam */
  def getHashAlgorithm: String = $(hashAlgorithm)

  /** @group setParam */
  def setHashAlgorithm(value: String): this.type = set(hashAlgorithm, value)

  @Since("2.0.0")
  override def transform(dataset: Dataset[_]): DataFrame = {
    val outputSchema = transformSchema(dataset.schema)
    val hashingTF = new feature.HashingTF($(numFeatures))
      .setBinary($(binary))
      .setHashAlgorithm($(hashAlgorithm))
    val t = udf { terms: Seq[_] => hashingTF.transform(terms) }
    val metadata = outputSchema($(outputCol)).metadata
    dataset.select(col("*"), t(col($(inputCol))).as($(outputCol), metadata))
  }

  override def transformSchema(schema: StructType): StructType = {
    val inputType = schema($(inputCol)).dataType
    require(inputType.isInstanceOf[ArrayType],
      s"The input column must be ArrayType, but got $inputType.")
    val attrGroup = new AttributeGroup($(outputCol), $(numFeatures))
    SchemaUtils.appendColumn(schema, attrGroup.toStructField())
  }

  override def copy(extra: ParamMap): HashingTF = defaultCopy(extra)
}

@Since("1.6.0")
object HashingTF extends DefaultParamsReadable[HashingTF] {

  @Since("1.6.0")
  override def load(path: String): HashingTF = super.load(path)
}
