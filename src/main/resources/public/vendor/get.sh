#!/bin/bash

rm -rf *.js
rm -rf *.ts

curl -sLo babylon.js https://github.com/BabylonJS/Babylon.js/raw/master/dist/preview%20release/babylon.max.js
curl -sLo babylon.d.ts https://github.com/BabylonJS/Babylon.js/raw/master/dist/preview%20release/babylon.d.ts

curl -sLo babylon.gui.js https://github.com/BabylonJS/Babylon.js/raw/master/dist/preview%20release/gui/babylon.gui.js
curl -sLo babylon.gui.d.ts https://github.com/BabylonJS/Babylon.js/raw/master/dist/preview%20release/gui/babylon.gui.d.ts

curl -sLo pep.js https://code.jquery.com/pep/0.4.3/pep.js

