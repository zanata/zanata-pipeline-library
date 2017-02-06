void printNode() {
  println "running on node ${env.NODE_NAME}"
}

void printEnv() {
  sh "env|sort"
}
