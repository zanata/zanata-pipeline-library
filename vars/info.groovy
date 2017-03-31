class info implements Serializable {
  void printNode(env) {
    println "running on node ${env.NODE_NAME}"
  }

  void printEnv() {
    sh "env|sort"
  }
}
