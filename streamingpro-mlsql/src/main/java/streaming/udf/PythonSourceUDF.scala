package streaming.udf

import java.util.UUID

import org.apache.spark.sql.catalyst.JavaTypeInference
import org.apache.spark.sql.types._
import org.python.core._
import streaming.common.{ScriptCacheKey, SourceCodeCompiler}
import streaming.jython.JythonUtils

import scala.collection.mutable.ArrayBuffer

/**
  * Created by allwefantasy on 28/8/2018.
  */
object PythonSourceUDF {

  private def wrapClass(function: String) = {
    val temp = function.split("\n").map(f => s"\t$f").mkString("\n")
    val className = s"StreamingProUDF_${UUID.randomUUID().toString.replaceAll("-", "")}"
    val newfun =
      s"""
         |class  ${className}:
         |${temp}
         """.stripMargin
    (className, newfun)

  }

  def apply(src: String, methodName: Option[String], returnType: String): (AnyRef, DataType) = {
    val (className, newfun) = wrapClass(src)
    apply(newfun, className, methodName, returnType)
  }


  def apply(src: String, className: String, methodName: Option[String], returnType: String): (AnyRef, DataType) = {
    val argumentNum = getParameterCount(src, className, methodName)
    (generateFunction(src, className, methodName, argumentNum), toSparkType(returnType))
  }

  private def findInputInArrayBracket(input: String) = {
    val max = input.length - 1
    val rest = ArrayBuffer[Char]()
    var firstS = false
    var fBracketCount = 0
    (0 until max).foreach { i =>
      input(i) match {
        case '(' =>
          if (firstS) {
            rest += input(i)
            fBracketCount += 1
          } else {
            firstS = true
          }
        case ')' => fBracketCount -= 1
          if (fBracketCount < 0) {
            firstS = false
          } else {
            rest += input(i)
          }
        case _ =>
          if (firstS) {
            rest += input(i)
          }

      }
    }
    rest.mkString("")
  }

  private def findKeyAndValue(input: String) = {
    val max = input.length - 1
    var fBracketCount = 0
    var position = 0
    (0 until max).foreach { i =>
      input(i) match {
        case '(' =>
          fBracketCount += 1
        case ')' =>
          fBracketCount -= 1
        case ',' =>
          if (fBracketCount == 0) {
            position = i
          }
        case _ =>
      }
    }
    (input.substring(0, position), input.substring(position + 1))
  }

  //array(array(map(string,string)))
  private def toSparkType(dt: String): DataType = dt match {
    case "boolean" => BooleanType
    case "byte" => ByteType
    case "short" => ShortType
    case "integer" => IntegerType
    case "date" => DateType
    case "long" => LongType
    case "float" => FloatType
    case "double" => DoubleType
    case "decimal" => DoubleType
    case "binary" => BinaryType
    case "string" => StringType
    case c: String if c.startsWith("array") =>
      ArrayType(toSparkType(findInputInArrayBracket(c)))
    case c: String if c.startsWith("map") =>
      //map(map(string,string),string)
      val (key, value) = findKeyAndValue(findInputInArrayBracket(c))
      MapType(toSparkType(key), toSparkType(value))

    case _ => throw new RuntimeException("dt is not found spark type")

  }

  private def getParameterCount(src: String, classMethod: String, methodName: Option[String]): Int = {
    val po = SourceCodeCompiler.execute(ScriptCacheKey(src, classMethod, "python"))
    val pi = po.asInstanceOf[PyObject].__getattr__(methodName.getOrElse("apply")).asInstanceOf[PyMethod]
    pi.__func__.asInstanceOf[PyFunction].__code__.asInstanceOf[PyTableCode].co_argcount - 1
  }

  def generateFunction(src: String, className: String, methodName: Option[String], argumentNum: Int): AnyRef = {
    lazy val instance = SourceCodeCompiler.execute(ScriptCacheKey(src, className, "python")).asInstanceOf[PyObject].__call__()
    lazy val method = instance.__getattr__(methodName.getOrElse("apply"))
    argumentNum match {
      case 0 => new Function0[Any] with Serializable {
        override def apply(): Any = {
          JythonUtils.toJava(method.__call__())
        }
      }
      case 1 => new Function1[Object, Any] with Serializable {
        override def apply(v1: Object): Any = {
          JythonUtils.toJava(method.__call__(JythonUtils.toPy(v1)))
        }
      }
      case 2 => new Function2[Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object): Any = {
          JythonUtils.toJava(method.__call__(JythonUtils.toPy(v1), JythonUtils.toPy(v2)))
        }
      }
      case 3 => new Function3[Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object): Any = {
          JythonUtils.toJava(method.__call__(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3)))
        }
      }
      case 4 => new Function4[Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object): Any = {
          JythonUtils.toJava(method.__call__(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4)))
        }
      }
      case 5 => new Function5[Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5))))
        }
      }
      case 6 => new Function6[Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6))))
        }
      }
      case 7 => new Function7[Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7))))
        }
      }
      case 8 => new Function8[Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8))))
        }
      }
      case 9 => new Function9[Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9))))
        }
      }
      case 10 => new Function10[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10))))
        }
      }
      case 11 => new Function11[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10), JythonUtils.toPy(v11))))
        }
      }
      case 12 => new Function12[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10), JythonUtils.toPy(v11), JythonUtils.toPy(v12))))
        }
      }
      case 13 => new Function13[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10), JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13))))
        }
      }
      case 14 => new Function14[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14))))
        }
      }
      case 15 => new Function15[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15))))
        }
      }
      case 16 => new Function16[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15), JythonUtils.toPy(v16))))
        }
      }
      case 17 => new Function17[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object, v17: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15), JythonUtils.toPy(v16), JythonUtils.toPy(v17))))
        }
      }
      case 18 => new Function18[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object, v17: Object, v18: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15), JythonUtils.toPy(v16), JythonUtils.toPy(v17), JythonUtils.toPy(v18))))
        }
      }
      case 19 => new Function19[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object, v17: Object, v18: Object, v19: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15), JythonUtils.toPy(v16), JythonUtils.toPy(v17), JythonUtils.toPy(v18), JythonUtils.toPy(v19))))
        }
      }
      case 20 => new Function20[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object, v17: Object, v18: Object, v19: Object, v20: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15),
            JythonUtils.toPy(v16), JythonUtils.toPy(v17), JythonUtils.toPy(v18), JythonUtils.toPy(v19), JythonUtils.toPy(v20))))
        }
      }
      case 21 => new Function21[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object, v17: Object, v18: Object, v19: Object, v20: Object, v21: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15),
            JythonUtils.toPy(v16), JythonUtils.toPy(v17), JythonUtils.toPy(v18), JythonUtils.toPy(v19), JythonUtils.toPy(v20), JythonUtils.toPy(v21))))
        }
      }
      case 22 => new Function22[Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Object, Any] with Serializable {
        override def apply(v1: Object, v2: Object, v3: Object, v4: Object, v5: Object, v6: Object, v7: Object, v8: Object, v9: Object, v10: Object, v11: Object, v12: Object, v13: Object, v14: Object, v15: Object, v16: Object, v17: Object, v18: Object, v19: Object, v20: Object, v21: Object, v22: Object): Any = {
          JythonUtils.toJava(method.__call__(Array(JythonUtils.toPy(v1), JythonUtils.toPy(v2), JythonUtils.toPy(v3), JythonUtils.toPy(v4), JythonUtils.toPy(v5), JythonUtils.toPy(v6), JythonUtils.toPy(v7), JythonUtils.toPy(v8), JythonUtils.toPy(v9), JythonUtils.toPy(v10),
            JythonUtils.toPy(v11), JythonUtils.toPy(v12), JythonUtils.toPy(v13), JythonUtils.toPy(v14), JythonUtils.toPy(v15),
            JythonUtils.toPy(v16), JythonUtils.toPy(v17), JythonUtils.toPy(v18), JythonUtils.toPy(v19), JythonUtils.toPy(v20), JythonUtils.toPy(v21), JythonUtils.toPy(v22))))
        }
      }
      case n => throw new Exception(s"UDF with $n arguments is not supported ")
    }
  }
}
