def call(Closure wrapped) {
  // NB this wrapper requires a node
  wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm', 'defaultFg': 1, 'defaultBg': 2]) {
    wrapped.call()
  }
}
