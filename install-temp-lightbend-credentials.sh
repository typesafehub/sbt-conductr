#!/usr/bin/env bash
mkdir $HOME/.lightbend/ &&
cat > $HOME/.lightbend/commercial.credentials <<EOL
realm = Bintray
host = lightbend.bintray.com
user = f9095e3b-542c-458a-93e7-418d3218f3fb@lightbend
password = 2bff26deceadc129a7a52b791d4f68589d56a542
EOL
