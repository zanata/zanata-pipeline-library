package org.zanata.jenkins

import com.cloudbees.groovy.cps.NonCPS

/**
 * @author Sean Flanigan <a href="mailto:sflaniga@redhat.com">sflaniga@redhat.com</a>
 */
class Strings implements Serializable {

  // Based on http://stackoverflow.com/a/37688740/14379
  @NonCPS
  static String truncateAtWord(String content, int maxLength) {
    def ellipsis = "…"
    // Is content > than the maxLength?
    if (content.size() > maxLength) {
      def bi = java.text.BreakIterator.getWordInstance()
      bi.setText(content);
      def cutoff = bi.preceding(maxLength - ellipsis.length())
      // if too short when cutting by words, ignore words
      if (cutoff < maxLength / 2) {
        cutoff = maxLength - ellipsis.length()
      }
      // Truncate
      return content.substring(0, cutoff) + ellipsis
    } else {
      return content
    }
  }
}
