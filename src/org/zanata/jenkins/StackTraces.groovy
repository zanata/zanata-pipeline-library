package org.zanata.jenkins

class StackTraces implements Serializable {

  static String getStackTrace(Throwable e) {
    def stringWriter = new StringWriter();
    e.printStackTrace(new PrintWriter(stringWriter));
    return stringWriter.toString()
  }
}
