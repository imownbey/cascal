import sbt._

class CascalProject(info:ProjectInfo) extends DefaultProject(info) {
  val shorrockin = "Shorrockin Repository" at "http://maven.shorrockin.com"

  /**
   * any class ending with Spec or Unit will be picked up and ran by sbt
   */
  override def includeTest(s: String) = { s.endsWith("Spec") || s.endsWith("Unit") || s.startsWith("Test") }
}