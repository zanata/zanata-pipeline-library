package org.zanata.jenkins

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
class StackTraces implements Serializable {

  static String getStackTrace(Throwable e) {
    def stringWriter = new StringWriter();
    e.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString()
  }
}
