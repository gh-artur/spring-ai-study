#!/usr/bin/env bash
# Normaliza um subprojeto recem-gerado (Spring Initializr) para o padrao deste repo.
#
#   Uso: scripts/novo-subprojeto.sh <pasta-do-subprojeto>
#
# Faz a parte mecanica descrita em docs/ADICIONAR_SUBPROJETO.md:
#   1. remove .gitignore/.gitattributes locais (redundantes com os da raiz)
#   2. avisa se houver caminho absoluto em application*.properties
#   3. avisa se usar Lombok sem annotationProcessorPaths no pom.xml
set -euo pipefail

dir="${1:-}"
[ -z "$dir" ] && { echo "uso: $0 <pasta-do-subprojeto>"; exit 1; }
dir="${dir%/}"
[ -d "$dir" ]        || { echo "erro: '$dir' nao e uma pasta"; exit 1; }
[ -f "$dir/pom.xml" ] || { echo "erro: '$dir/pom.xml' nao encontrado"; exit 1; }

# 1) remove dotfiles redundantes (a raiz ja cobre)
for f in .gitignore .gitattributes; do
  if [ -f "$dir/$f" ]; then
    rm -f "$dir/$f"
    echo "removido: $dir/$f (coberto pela raiz)"
  fi
done

# 2) avisa sobre caminhos absolutos em properties
if grep -rInE 'jdbc:h2:file:[A-Za-z]:\\|:file:/(home|Users)/' "$dir"/src/main/resources/*.properties 2>/dev/null; then
  echo "AVISO: caminho absoluto acima -> troque por relativo (ex.: ./chatmemory)"
fi

# 3) Lombok precisa de annotationProcessorPaths no JDK 23+
if grep -q 'projectlombok' "$dir/pom.xml" && ! grep -q 'annotationProcessorPaths' "$dir/pom.xml"; then
  echo "AVISO: pom usa Lombok mas nao tem annotationProcessorPaths."
  echo "       Adicione o maven-compiler-plugin (veja docs/ADICIONAR_SUBPROJETO.md)."
fi

echo "ok: '$dir' normalizado."
