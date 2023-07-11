package handlers

import org.apache.commons.lang3.StringUtils
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.xssf.usermodel.{XSSFRow, XSSFWorkbook}
import org.slf4j.{Logger, LoggerFactory}
import org.sunbird.cache.impl.RedisCache
import utils.Constants
import java.io.{File, FileInputStream}
import java.util
import scala.collection.JavaConverters._
import scala.util.control.Breaks._

object QuestionExcelParser {

  private val logger: Logger = LoggerFactory.getLogger(RedisCache.getClass.getCanonicalName)

  def getQuestions(fileName: String, file: File) = {
    try {
      val workbook = new XSSFWorkbook(new FileInputStream(file))
      val sheets = (2 until workbook.getNumberOfSheets).map(index => workbook.getSheetAt(index))  // iterates over the excelsheet
      sheets.flatMap(sheet => {
        logger.info("Inside the getQuestions")
        (1 until sheet.getPhysicalNumberOfRows)  // iterates over each row in the sheet
          .filter(rowNum => {
            val oRow = Option(sheet.getRow(rowNum))
            // matching the row value to determine the value of objects
            oRow match {
              case Some(x) => {
                val questionType = sheet.getRow(rowNum).getCell(10)
                val isMCQ = questionType.toString.trim.equalsIgnoreCase(Constants.MCQ) || (questionType.toString.trim.startsWith(Constants.MCQ)
                || questionType.toString.trim.endsWith(Constants.MCQ))// checks questionType is MCQ
                //val isMTF = Constants.MTF.equals(questionType.toString) || Constants.MATCH_THE_FOLLOWING.equals(questionType.toString)
                //val isFITB = Constants.FITB.equals(questionType.toString)
                val answerCell = sheet.getRow(rowNum).getCell(9)
                val isAnswerNotBlank = answerCell.getCellType() != CellType.BLANK
                //isMCQ || isMTF || isFITB && isAnswerNotBlank
                isMCQ && isAnswerNotBlank
              }
              case None => false
            }
          })
          .map(rowNum => parseQuestion(sheet.getRow(rowNum))).toList
      })
    }

    catch {
      case e: Exception => throw new Exception("Invalid File")
    }
  }

  def buildDefaultQuestion() = {
    val defaultQuestion = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]

    defaultQuestion.put("code", "question")
    defaultQuestion.put("mimeType", "application/vnd.sunbird.question")
    defaultQuestion.put("objectType", "Question")
    defaultQuestion.put("primaryCategory", "Multiple Choice Question")
    defaultQuestion.put("qType", "MCQ")
    defaultQuestion.put("name", "Question")
    defaultQuestion
  }

  def buildOptionMap(option: String, level: Integer, answer: Boolean) = {
    val mapOptionValue = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    mapOptionValue.put("body", option)
    mapOptionValue.put("value", level)
    val mapOption = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    mapOption.put("answer", answer.asInstanceOf[AnyRef])
    mapOption.put("value", mapOptionValue)
    mapOption
  }

  def buildInteractionMap(option: String, level: Integer) = {
    val mapOptionValue = new java.util.HashMap().asInstanceOf[java.util.Map[String, AnyRef]]
    mapOptionValue.put("label", option)
    mapOptionValue.put("value", level)
    mapOptionValue
  }

  // determines whether the Opton is correct
  def isOptionAnswer(optSeq: String, answerText: String): Boolean = {

    val correctOpt = answerText.split("[,\n]").map(_.trim)

    var boolean = false
    breakable {
      for (index <- 0 until correctOpt.size) {
          boolean = correctOpt.apply(index).toLowerCase.startsWith(optSeq.toLowerCase)
          if (boolean.equals(true)) {
            break()
          }
        }
    }
    boolean
  }

  def parseQuestion(xssFRow: XSSFRow) = {
    val question = buildDefaultQuestion()

    val rowContent = (0 until xssFRow.getPhysicalNumberOfCells)
      .map(colId => Option(xssFRow.getCell(colId)).getOrElse("").toString).toList

    //fetches data from sheet
    // this is competency label
    val medium = rowContent.apply(2)
    // this is the role(subject)
    val subject = rowContent.apply(0)
    // this val is for competency level label
    val difficultyLevel = rowContent.apply(4)
    // this val is for activity(GradeLevel)
    val gradeLevel = rowContent.apply(6)
    val questionText = rowContent.apply(7)
    val answer = rowContent.apply(9).trim
    val board = rowContent.apply(11).trim


    var i = -1
    val options = new util.ArrayList[util.Map[String, AnyRef]](rowContent.apply(8).split("\n").filter(StringUtils.isNotBlank).map(o => {
      val option = o.split("[.).]").toList
      val optSeq = option.apply(0).trim

      val optText = option.apply(1).trim
      i += 1
      buildOptionMap(optText, i, isOptionAnswer(optSeq, answer))
    }).toList.asJava)

    var j = -1
    val mapRepsonse = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    val repsonse1 = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    val responseDeclarationOption = new util.ArrayList[util.Map[String, AnyRef]](rowContent.apply(8).split("\n").filter(StringUtils.isNotBlank).map(o => {
      val option = o.split("[.).]").toList
      val optSeq = option.apply(0).trim

      val optText = option.apply(1).trim
      j += 1
      if(isOptionAnswer(optSeq, answer)){
        mapRepsonse.put("maxScore", 1.asInstanceOf[AnyRef])
        mapRepsonse.put("cardinality", "single")
        mapRepsonse.put("type", "integer")
        val mapCorrectResponse = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
        mapCorrectResponse.put("value", String.valueOf(j))
        val mapOutcomes = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
        mapOutcomes.put("SCORE", 1.asInstanceOf[AnyRef])
        mapCorrectResponse.put("outcomes", mapOutcomes)
        mapRepsonse.put("correctResponse", mapCorrectResponse)
        mapRepsonse.put("mapping", new util.ArrayList())
      }
      repsonse1.put("response1", mapRepsonse)
      repsonse1
    }).toList.asJava)

    var k = -1
    val interactionOptions = new util.ArrayList[util.Map[String, AnyRef]](rowContent.apply(8).split("\n").filter(StringUtils.isNotBlank).map(o => {
      val option = o.split("[.).]").toList
      val optSeq = option.apply(0).trim

      val optText = option.apply(1).trim
      k += 1
      buildInteractionMap(optText, k)
    }).toList.asJava)

    val mapInteraction: _root_.java.util.Map[_root_.java.lang.String, AnyRef] = createInteractionMap(interactionOptions)
    val editorState = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    val interactionTypes = new util.ArrayList[String]()
    interactionTypes.add("choice")
    question.put("board", board)
    question.put("interactionTypes",interactionTypes)
    question.put("interactions",mapInteraction)
    question.put("responseDeclaration",repsonse1)
    setArrayValue(question, medium, Constants.medium)
    setArrayValue(question, subject, Constants.subject)
    setArrayValue(question, gradeLevel, Constants.gradeLevel)
    setArrayValue(question, difficultyLevel, Constants.difficultyLevel)
    editorState.put("options", options)
    editorState.put("question", questionText)
    logger.info("Inside the parseQuestion")
    question.put("body", questionText)
    question.put("editorState", editorState)
    question.put("templateId", "mcq-vertical")
    question.put("answer", answer)
    question
  }


  private def createInteractionMap(interactionOptions: util.ArrayList[util.Map[String, AnyRef]]) = {
    val mapOption = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    mapOption.put("type", "choice".asInstanceOf[AnyRef])
    mapOption.put("options", interactionOptions)
    val mapValidation = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    mapValidation.put("required", "Yes".asInstanceOf[AnyRef])
    val mapInteraction = new util.HashMap().asInstanceOf[util.Map[String, AnyRef]]
    mapInteraction.put("response1", mapOption)
    mapInteraction.put("validation", mapValidation)
    mapInteraction
  }

  private def setArrayValue(question: util.Map[String, AnyRef], medium: String, questionKey: String) = {
    val valueList = new util.ArrayList[String]();
    valueList.add(medium);
    question.put(questionKey, valueList)
  }
}
