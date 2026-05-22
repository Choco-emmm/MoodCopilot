/*
 * ATTENTION: The "eval" devtool has been used (maybe by default in mode: "development").
 * This devtool is neither made for production nor for readable output files.
 * It uses "eval()" calls to create a separate source file in the browser devtools.
 * If you are trying to read the output file, select a different devtool (https://webpack.js.org/configuration/devtool/)
 * or disable the default devtool with "devtool: false".
 * If you are looking for production-ready output files, see mode: "production" (https://webpack.js.org/configuration/mode/).
 */
/******/ (() => { // webpackBootstrap
/******/ 	"use strict";
/******/ 	var __webpack_modules__ = ({

/***/ "./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js":
/*!*******************************************************************************!*\
  !*** ./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js ***!
  \*******************************************************************************/
/***/ ((module, __unused_webpack_exports, __webpack_require__) => {

eval("\n\n/* eslint-env browser */\n/*\n  eslint-disable\n  no-console,\n  func-names\n*/\n\n/** @typedef {any} TODO */\n\nvar normalizeUrl = __webpack_require__(/*! ./normalize-url */ \"./node_modules/mini-css-extract-plugin/dist/hmr/normalize-url.js\");\nvar srcByModuleId = Object.create(null);\nvar noDocument = typeof document === \"undefined\";\nvar forEach = Array.prototype.forEach;\n\n/**\n * @param {function} fn\n * @param {number} time\n * @returns {(function(): void)|*}\n */\nfunction debounce(fn, time) {\n  var timeout = 0;\n  return function () {\n    // @ts-ignore\n    var self = this;\n    // eslint-disable-next-line prefer-rest-params\n    var args = arguments;\n    var functionCall = function functionCall() {\n      return fn.apply(self, args);\n    };\n    clearTimeout(timeout);\n\n    // @ts-ignore\n    timeout = setTimeout(functionCall, time);\n  };\n}\nfunction noop() {}\n\n/**\n * @param {TODO} moduleId\n * @returns {TODO}\n */\nfunction getCurrentScriptUrl(moduleId) {\n  var src = srcByModuleId[moduleId];\n  if (!src) {\n    if (document.currentScript) {\n      src = /** @type {HTMLScriptElement} */document.currentScript.src;\n    } else {\n      var scripts = document.getElementsByTagName(\"script\");\n      var lastScriptTag = scripts[scripts.length - 1];\n      if (lastScriptTag) {\n        src = lastScriptTag.src;\n      }\n    }\n    srcByModuleId[moduleId] = src;\n  }\n\n  /**\n   * @param {string} fileMap\n   * @returns {null | string[]}\n   */\n  return function (fileMap) {\n    if (!src) {\n      return null;\n    }\n    var splitResult = src.split(/([^\\\\/]+)\\.js$/);\n    var filename = splitResult && splitResult[1];\n    if (!filename) {\n      return [src.replace(\".js\", \".css\")];\n    }\n    if (!fileMap) {\n      return [src.replace(\".js\", \".css\")];\n    }\n    return fileMap.split(\",\").map(function (mapRule) {\n      var reg = new RegExp(\"\".concat(filename, \"\\\\.js$\"), \"g\");\n      return normalizeUrl(src.replace(reg, \"\".concat(mapRule.replace(/{fileName}/g, filename), \".css\")));\n    });\n  };\n}\n\n/**\n * @param {TODO} el\n * @param {string} [url]\n */\nfunction updateCss(el, url) {\n  if (!url) {\n    if (!el.href) {\n      return;\n    }\n\n    // eslint-disable-next-line\n    url = el.href.split(\"?\")[0];\n  }\n  if (!isUrlRequest( /** @type {string} */url)) {\n    return;\n  }\n  if (el.isLoaded === false) {\n    // We seem to be about to replace a css link that hasn't loaded yet.\n    // We're probably changing the same file more than once.\n    return;\n  }\n  if (!url || !(url.indexOf(\".css\") > -1)) {\n    return;\n  }\n\n  // eslint-disable-next-line no-param-reassign\n  el.visited = true;\n  var newEl = el.cloneNode();\n  newEl.isLoaded = false;\n  newEl.addEventListener(\"load\", function () {\n    if (newEl.isLoaded) {\n      return;\n    }\n    newEl.isLoaded = true;\n    el.parentNode.removeChild(el);\n  });\n  newEl.addEventListener(\"error\", function () {\n    if (newEl.isLoaded) {\n      return;\n    }\n    newEl.isLoaded = true;\n    el.parentNode.removeChild(el);\n  });\n  newEl.href = \"\".concat(url, \"?\").concat(Date.now());\n  if (el.nextSibling) {\n    el.parentNode.insertBefore(newEl, el.nextSibling);\n  } else {\n    el.parentNode.appendChild(newEl);\n  }\n}\n\n/**\n * @param {string} href\n * @param {TODO} src\n * @returns {TODO}\n */\nfunction getReloadUrl(href, src) {\n  var ret;\n\n  // eslint-disable-next-line no-param-reassign\n  href = normalizeUrl(href);\n  src.some(\n  /**\n   * @param {string} url\n   */\n  // eslint-disable-next-line array-callback-return\n  function (url) {\n    if (href.indexOf(src) > -1) {\n      ret = url;\n    }\n  });\n  return ret;\n}\n\n/**\n * @param {string} [src]\n * @returns {boolean}\n */\nfunction reloadStyle(src) {\n  if (!src) {\n    return false;\n  }\n  var elements = document.querySelectorAll(\"link\");\n  var loaded = false;\n  forEach.call(elements, function (el) {\n    if (!el.href) {\n      return;\n    }\n    var url = getReloadUrl(el.href, src);\n    if (!isUrlRequest(url)) {\n      return;\n    }\n    if (el.visited === true) {\n      return;\n    }\n    if (url) {\n      updateCss(el, url);\n      loaded = true;\n    }\n  });\n  return loaded;\n}\nfunction reloadAll() {\n  var elements = document.querySelectorAll(\"link\");\n  forEach.call(elements, function (el) {\n    if (el.visited === true) {\n      return;\n    }\n    updateCss(el);\n  });\n}\n\n/**\n * @param {string} url\n * @returns {boolean}\n */\nfunction isUrlRequest(url) {\n  // An URL is not an request if\n\n  // It is not http or https\n  if (!/^[a-zA-Z][a-zA-Z\\d+\\-.]*:/.test(url)) {\n    return false;\n  }\n  return true;\n}\n\n/**\n * @param {TODO} moduleId\n * @param {TODO} options\n * @returns {TODO}\n */\nmodule.exports = function (moduleId, options) {\n  if (noDocument) {\n    console.log(\"no window.document found, will not HMR CSS\");\n    return noop;\n  }\n  var getScriptSrc = getCurrentScriptUrl(moduleId);\n  function update() {\n    var src = getScriptSrc(options.filename);\n    var reloaded = reloadStyle(src);\n    if (options.locals) {\n      console.log(\"[HMR] Detected local css modules. Reload all css\");\n      reloadAll();\n      return;\n    }\n    if (reloaded) {\n      console.log(\"[HMR] css reload %s\", src.join(\" \"));\n    } else {\n      console.log(\"[HMR] Reload all css\");\n      reloadAll();\n    }\n  }\n  return debounce(update, 50);\n};\n\n//# sourceURL=webpack://webpack-demo/./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js?");

/***/ }),

/***/ "./node_modules/mini-css-extract-plugin/dist/hmr/normalize-url.js":
/*!************************************************************************!*\
  !*** ./node_modules/mini-css-extract-plugin/dist/hmr/normalize-url.js ***!
  \************************************************************************/
/***/ ((module) => {

eval("\n\n/* eslint-disable */\n\n/**\n * @param {string[]} pathComponents\n * @returns {string}\n */\nfunction normalizeUrl(pathComponents) {\n  return pathComponents.reduce(function (accumulator, item) {\n    switch (item) {\n      case \"..\":\n        accumulator.pop();\n        break;\n      case \".\":\n        break;\n      default:\n        accumulator.push(item);\n    }\n    return accumulator;\n  }, /** @type {string[]} */[]).join(\"/\");\n}\n\n/**\n * @param {string} urlString\n * @returns {string}\n */\nmodule.exports = function (urlString) {\n  urlString = urlString.trim();\n  if (/^data:/i.test(urlString)) {\n    return urlString;\n  }\n  var protocol = urlString.indexOf(\"//\") !== -1 ? urlString.split(\"//\")[0] + \"//\" : \"\";\n  var components = urlString.replace(new RegExp(protocol, \"i\"), \"\").split(\"/\");\n  var host = components[0].toLowerCase().replace(/\\.$/, \"\");\n  components[0] = \"\";\n  var path = normalizeUrl(components);\n  return protocol + host + path;\n};\n\n//# sourceURL=webpack://webpack-demo/./node_modules/mini-css-extract-plugin/dist/hmr/normalize-url.js?");

/***/ }),

/***/ "./src/captcha/captcha.scss":
/*!**********************************!*\
  !*** ./src/captcha/captcha.scss ***!
  \**********************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326983\n      var cssReload = __webpack_require__(/*! ../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/captcha.scss?");

/***/ }),

/***/ "./src/captcha/common/common.scss":
/*!****************************************!*\
  !*** ./src/captcha/common/common.scss ***!
  \****************************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326961\n      var cssReload = __webpack_require__(/*! ../../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/common/common.scss?");

/***/ }),

/***/ "./src/captcha/concat/concat.scss":
/*!****************************************!*\
  !*** ./src/captcha/concat/concat.scss ***!
  \****************************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326960\n      var cssReload = __webpack_require__(/*! ../../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/concat/concat.scss?");

/***/ }),

/***/ "./src/captcha/disable/disable.scss":
/*!******************************************!*\
  !*** ./src/captcha/disable/disable.scss ***!
  \******************************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326984\n      var cssReload = __webpack_require__(/*! ../../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/disable/disable.scss?");

/***/ }),

/***/ "./src/captcha/image_click/image_click.scss":
/*!**************************************************!*\
  !*** ./src/captcha/image_click/image_click.scss ***!
  \**************************************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326964\n      var cssReload = __webpack_require__(/*! ../../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/image_click/image_click.scss?");

/***/ }),

/***/ "./src/captcha/rotate/rotate.scss":
/*!****************************************!*\
  !*** ./src/captcha/rotate/rotate.scss ***!
  \****************************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326958\n      var cssReload = __webpack_require__(/*! ../../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/rotate/rotate.scss?");

/***/ }),

/***/ "./src/captcha/slider/slider.scss":
/*!****************************************!*\
  !*** ./src/captcha/slider/slider.scss ***!
  \****************************************/
/***/ ((module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n// extracted by mini-css-extract-plugin\n\n    if(true) {\n      // 1779442326962\n      var cssReload = __webpack_require__(/*! ../../../node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js */ \"./node_modules/mini-css-extract-plugin/dist/hmr/hotModuleReplacement.js\")(module.id, {\"locals\":false});\n      module.hot.dispose(cssReload);\n      module.hot.accept(undefined, cssReload);\n    }\n  \n\n//# sourceURL=webpack://webpack-demo/./src/captcha/slider/slider.scss?");

/***/ }),

/***/ "./src/captcha/captcha.js":
/*!********************************!*\
  !*** ./src/captcha/captcha.js ***!
  \********************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   CaptchaConfig: () => (/* reexport safe */ _config_config__WEBPACK_IMPORTED_MODULE_6__.CaptchaConfig),\n/* harmony export */   TianAiCaptcha: () => (/* binding */ TianAiCaptcha)\n/* harmony export */ });\n/* harmony import */ var _captcha_scss__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./captcha.scss */ \"./src/captcha/captcha.scss\");\n/* harmony import */ var _slider_slider__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./slider/slider */ \"./src/captcha/slider/slider.js\");\n/* harmony import */ var _rotate_rotate__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./rotate/rotate */ \"./src/captcha/rotate/rotate.js\");\n/* harmony import */ var _concat_concat__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! ./concat/concat */ \"./src/captcha/concat/concat.js\");\n/* harmony import */ var _disable_disable__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! ./disable/disable */ \"./src/captcha/disable/disable.js\");\n/* harmony import */ var _word_image_click_word_image_click__WEBPACK_IMPORTED_MODULE_5__ = __webpack_require__(/*! ./word_image_click/word_image_click */ \"./src/captcha/word_image_click/word_image_click.js\");\n/* harmony import */ var _config_config__WEBPACK_IMPORTED_MODULE_6__ = __webpack_require__(/*! ./config/config */ \"./src/captcha/config/config.js\");\n/* harmony import */ var _common_common__WEBPACK_IMPORTED_MODULE_7__ = __webpack_require__(/*! ./common/common */ \"./src/captcha/common/common.js\");\n\r\n\r\n\r\n\r\n\r\n\r\n\r\n\r\nconst template =\r\n    `\r\n    <div id=\"tianai-captcha-parent\">\r\n        <div id=\"tianai-captcha-bg-img\"></div>\r\n        <div id=\"tianai-captcha-box\">\r\n            <div id=\"tianai-captcha-loading\" class=\"loading\"></div>\r\n        </div>\r\n        <!-- 底部 -->\r\n        <div class=\"slider-bottom\">\r\n            <img class=\"logo\" id=\"tianai-captcha-logo\" src=\"data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB4AAAAeCAMAAAAM7l6QAAAAMFBMVEVHcEz3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkX3tkVmTmjZAAAAD3RSTlMASbTm8wh12hOGoCNiyTV98jvOAAABB0lEQVR42nVT0aIFEQiMorD0/397Lc5a7J0n1UylgIniLRKyDcbBDudZH2DYCAabn3PmTrjeUX+7rJGWx0SqVpzReAfTtKU5fgVCNfxWjB69USUDGwoOiauHpZEpSr0tCx8ILb3Dm3WgBbAlifAJk6+Ww6wqEUmpmIorQVZ1JtqKnDMjkb7AgIpO/wMCaQbuBuEtsBUxhuD9daUaZnApiQB8NAKotMwirGGr6mbXpPnHLHDmy6oy3FgP+1j8IBdVklFc01xUJwv3NR0rIeXV5zpzdlruiijzNq/ufOeKWzZLP3160u5P8RjT1M+HHFtx+PwGyOZqT/D8ROOfjOInTLBIHjy/hvwHxkwPu5cCE1QAAAAASUVORK5CYII=\" id=\"tianai-captcha-logo\"></img>\r\n            <div class=\"close-btn\" id=\"tianai-captcha-slider-close-btn\"></div>\r\n            <div class=\"refresh-btn\" id=\"tianai-captcha-slider-refresh-btn\"></div>\r\n        </div>\r\n    </div>\r\n    `;\r\nfunction createCaptchaByType(type, tac) {\r\n    const box = tac.config.domBindEl.find(\"#tianai-captcha-box\");\r\n    const styleConfig = tac.style;\r\n    switch (type) {\r\n        case \"SLIDER\":\r\n            return new _slider_slider__WEBPACK_IMPORTED_MODULE_1__[\"default\"](box, styleConfig);\r\n        case \"ROTATE\":\r\n            return new _rotate_rotate__WEBPACK_IMPORTED_MODULE_2__[\"default\"](box, styleConfig);\r\n        case \"CONCAT\":\r\n            return new _concat_concat__WEBPACK_IMPORTED_MODULE_3__[\"default\"](box, styleConfig);\r\n        case \"WORD_IMAGE_CLICK\":\r\n            return new _word_image_click_word_image_click__WEBPACK_IMPORTED_MODULE_5__[\"default\"](box, styleConfig);\r\n        case \"DISABLED\":\r\n            return new _disable_disable__WEBPACK_IMPORTED_MODULE_4__[\"default\"](box, styleConfig);\r\n        default:\r\n            return null;\r\n    }\r\n}\r\nclass TianAiCaptcha {\r\n    constructor(config, style) {\r\n        this.config = (0,_config_config__WEBPACK_IMPORTED_MODULE_6__.wrapConfig)(config);\r\n        if (this.config.btnRefreshFun) {\r\n            this.btnRefreshFun = this.config.btnRefreshFun;\r\n        }\r\n        if (this.config.btnCloseFun) {\r\n            this.btnCloseFun = this.config.btnCloseFun;\r\n        }\r\n        this.style = (0,_config_config__WEBPACK_IMPORTED_MODULE_6__.wrapStyle)(style);\r\n    }\r\n\r\n    init() {\r\n        this.destroyWindow();\r\n        this.config.domBindEl.append(template);\r\n        this.domTemplate = this.config.domBindEl.find(\"#tianai-captcha-parent\");\r\n        (0,_common_common__WEBPACK_IMPORTED_MODULE_7__.clearAllPreventDefault)(this.domTemplate);\r\n        this.loadStyle();\r\n        // 绑定按钮事件\r\n        this.config.domBindEl.find(\"#tianai-captcha-slider-refresh-btn\").click((el) => {\r\n            this.btnRefreshFun(el, this);\r\n        });\r\n        this.config.domBindEl.find(\"#tianai-captcha-slider-close-btn\").click((el) => {\r\n            this.btnCloseFun(el, this);\r\n        });\r\n        // 加载验证码\r\n        this.reloadCaptcha();\r\n        return this;\r\n    }\r\n\r\n    btnRefreshFun(el, tac) {\r\n        tac.reloadCaptcha();\r\n    }\r\n    btnCloseFun(el, tac) {\r\n        tac.destroyWindow();\r\n    }\r\n    reloadCaptcha() {\r\n        this.showLoading();\r\n        this.destroyCaptcha(() => {\r\n            this.createCaptcha();\r\n        })\r\n    }\r\n    showLoading() {\r\n        this.config.domBindEl.find(\"#tianai-captcha-loading\").css(\"display\", \"block\");\r\n    }\r\n\r\n    closeLoading() {\r\n        this.config.domBindEl.find(\"#tianai-captcha-loading\").css(\"display\", \"none\");\r\n    }\r\n\r\n    loadStyle() {\r\n        // 设置样式\r\n        const bgUrl = this.style.bgUrl;\r\n        const logoUrl = this.style.logoUrl;\r\n        if (bgUrl) {\r\n            // 背景图片\r\n            this.config.domBindEl.find(\"#tianai-captcha-bg-img\").css(\"background-image\", \"url(\" + bgUrl + \")\");\r\n        }\r\n        if (logoUrl && logoUrl !== \"\") {\r\n            // logo\r\n            this.config.domBindEl.find(\"#tianai-captcha-logo\").attr(\"src\", logoUrl);\r\n        } else if (logoUrl === null){\r\n            // 删除logo\r\n            this.config.domBindEl.find(\"#tianai-captcha-logo\").css(\"display\", \"none\");\r\n        }\r\n    }\r\n\r\n    destroyWindow() {\r\n        if (this.C) {\r\n            this.C.destroy();\r\n            this.C = undefined;\r\n        }\r\n        if (this.domTemplate) {\r\n            this.domTemplate.remove();\r\n        }\r\n    }\r\n\r\n    openCaptcha() {\r\n        setTimeout(() => {\r\n\r\n            this.C.el.css(\"transform\", \"translateX(0)\")\r\n        }, 10)\r\n    }\r\n\r\n    createCaptcha() {\r\n        this.config.requestCaptchaData().then(data => {\r\n            this.closeLoading();\r\n            if (!data.code) {\r\n                throw new Error(\"[TAC] 后台验证码接口数据错误!!!\");\r\n            }\r\n            let captchaType = data.code === 200 ? data.data?.type : \"DISABLED\"\r\n            const captcha = createCaptchaByType(captchaType, this);\r\n            if (captcha == null) {\r\n                throw new Error(\"[TAC] 未知的验证码类型[\" + captchaType + \"]\");\r\n            }\r\n            captcha.init(data, (d, c) => {\r\n                // 验证\r\n                const currentCaptchaData = c.currentCaptchaData;\r\n                const data = {\r\n                    bgImageWidth: currentCaptchaData.bgImageWidth,\r\n                    bgImageHeight: currentCaptchaData.bgImageHeight,\r\n                    templateImageWidth: currentCaptchaData.templateImageWidth,\r\n                    templateImageHeight: currentCaptchaData.templateImageHeight,\r\n                    startTime: currentCaptchaData.startTime.getTime(),\r\n                    stopTime: currentCaptchaData.stopTime.getTime(),\r\n                    trackList: currentCaptchaData.trackList,\r\n                };\r\n                if (c.type === 'ROTATE_DEGREE' || c.type === 'ROTATE') {\r\n                    data.bgImageWidth = c.currentCaptchaData.end;\r\n                }\r\n                if (currentCaptchaData.data) {\r\n                    data.data = currentCaptchaData.data;\r\n                }\r\n                // 清空\r\n                const id = c.currentCaptchaData.currentCaptchaId;\r\n                c.currentCaptchaData = undefined;\r\n                // 调用验证接口\r\n                this.config.validCaptcha(id, data, c, this)\r\n            })\r\n            this.C = captcha;\r\n            this.openCaptcha()\r\n        });\r\n    }\r\n\r\n    destroyCaptcha(callback) {\r\n        if (this.C) {\r\n            this.C.el.css(\"transform\", \"translateX(300px)\")\r\n            setTimeout(() => {\r\n                this.C.destroy();\r\n                if (callback) {\r\n                    callback();\r\n                }\r\n            }, 500)\r\n        } else {\r\n            callback();\r\n        }\r\n    }\r\n\r\n\r\n}\r\n\r\n\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/captcha.js?");

/***/ }),

/***/ "./src/captcha/common/common.js":
/*!**************************************!*\
  !*** ./src/captcha/common/common.js ***!
  \**************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   CommonCaptcha: () => (/* binding */ CommonCaptcha),\n/* harmony export */   Dom: () => (/* binding */ Dom),\n/* harmony export */   DomEl: () => (/* binding */ DomEl),\n/* harmony export */   clearAllPreventDefault: () => (/* binding */ clearAllPreventDefault),\n/* harmony export */   closeTips: () => (/* binding */ closeTips),\n/* harmony export */   destroyEvent: () => (/* binding */ destroyEvent),\n/* harmony export */   down: () => (/* binding */ down),\n/* harmony export */   http: () => (/* binding */ http),\n/* harmony export */   initConfig: () => (/* binding */ initConfig),\n/* harmony export */   isEmptyObject: () => (/* binding */ isEmptyObject),\n/* harmony export */   move: () => (/* binding */ move),\n/* harmony export */   showTips: () => (/* binding */ showTips),\n/* harmony export */   up: () => (/* binding */ up)\n/* harmony export */ });\n/** 是否打印日志 */\r\nvar isPrintLog = false;\r\n\r\nfunction printLog(params) {\r\n    if (isPrintLog) {\r\n        console.log(JSON.stringify(params));\r\n    }\r\n}\r\n\r\n/**\r\n * 清除默认事件\r\n * @param event event\r\n */\r\nfunction clearPreventDefault(event) {\r\n    if (event.preventDefault) {\r\n        event.preventDefault();\r\n    }\r\n}\r\n\r\n/**\r\n * 阻止某div默认事件\r\n * @param dom\r\n */\r\nfunction clearAllPreventDefault(dom) {\r\n    Dom(dom).each((el) => {\r\n        // 手机端\r\n        el.addEventListener('touchmove', clearPreventDefault, {passive: false});\r\n        // pc端\r\n        el.addEventListener('mousemove', clearPreventDefault, {passive: false});\r\n    });\r\n}\r\n\r\nfunction reductionAllPreventDefault(dom) {\r\n    Dom(dom).each(function (el) {\r\n        el.removeEventListener('touchmove', clearPreventDefault);\r\n        el.addEventListener('mousemove', clearPreventDefault);\r\n    });\r\n}\r\n\r\n/**\r\n * 获取当前坐标\r\n * @param event 事件\r\n * @returns {{x: number, y: number}}\r\n */\r\nfunction getCurrentCoordinate(event) {\r\n    if (event.pageX !== null && event.pageX !== undefined) {\r\n        return {\r\n            x: Math.round(event.pageX),\r\n            y: Math.round(event.pageY)\r\n        }\r\n    }\r\n    let targetTouches;\r\n    if (event.changedTouches) {\r\n        // 抬起事件\r\n        targetTouches = event.changedTouches;\r\n    } else if (event.targetTouches) {\r\n        // pc 按下事件\r\n        targetTouches = event.targetTouches;\r\n    } else if (event.originalEvent && event.originalEvent.targetTouches) {\r\n        // 鼠标触摸事件\r\n        targetTouches = event.originalEvent.targetTouches;\r\n    }\r\n    if (targetTouches[0].pageX !== null && targetTouches[0].pageX !== undefined) {\r\n        return {\r\n            x: Math.round(targetTouches[0].pageX),\r\n            y: Math.round(targetTouches[0].pageY)\r\n        }\r\n    }\r\n    return {\r\n        x: Math.round(targetTouches[0].clientX),\r\n        y: Math.round(targetTouches[0].clientY)\r\n    }\r\n}\r\n\r\nfunction down(currentCaptcha, event) {\r\n    // debugger\r\n    const coordinate = getCurrentCoordinate(event);\r\n    let startX = coordinate.x;\r\n    let startY = coordinate.y;\r\n    currentCaptcha.currentCaptchaData.startX = startX;\r\n    currentCaptcha.currentCaptchaData.startY = startY;\r\n    const trackList = currentCaptcha.currentCaptchaData.trackList;\r\n    currentCaptcha.currentCaptchaData.startTime = new Date();\r\n    const startTime = currentCaptcha.currentCaptchaData.startTime;\r\n\r\n    trackList.push({\r\n        x: coordinate.x,\r\n        y: coordinate.y,\r\n        type: \"down\",\r\n        t: (new Date().getTime() - startTime.getTime())\r\n    });\r\n    printLog([\"start\", startX, startY])\r\n    currentCaptcha.__m__ = move.bind(null, currentCaptcha);\r\n    currentCaptcha.__u__ = up.bind(null, currentCaptcha);\r\n    // pc\r\n    window.addEventListener(\"mousemove\", currentCaptcha.__m__);\r\n    window.addEventListener(\"mouseup\", currentCaptcha.__u__);\r\n    // 手机端\r\n    window.addEventListener(\"touchmove\", currentCaptcha.__m__, false);\r\n    window.addEventListener(\"touchend\", currentCaptcha.__u__, false);\r\n    if (currentCaptcha && currentCaptcha.doDown) {\r\n        currentCaptcha.doDown(event, currentCaptcha)\r\n    }\r\n}\r\n\r\nfunction move(currentCaptcha, event) {\r\n    if (event.touches && event.touches.length > 0) {\r\n        event = event.touches[0];\r\n    }\r\n    // debugger\r\n    const coordinate = getCurrentCoordinate(event);\r\n    let pageX = coordinate.x;\r\n    let pageY = coordinate.y;\r\n    const startX = currentCaptcha.currentCaptchaData.startX;\r\n    const startY = currentCaptcha.currentCaptchaData.startY;\r\n    const startTime = currentCaptcha.currentCaptchaData.startTime;\r\n    const end = currentCaptcha.currentCaptchaData.end;\r\n    const bgImageWidth = currentCaptcha.currentCaptchaData.bgImageWidth;\r\n    const trackList = currentCaptcha.currentCaptchaData.trackList;\r\n    let moveX = pageX - startX;\r\n    let moveY = pageY - startY;\r\n    const track = {\r\n        x: coordinate.x,\r\n        y: coordinate.y,\r\n        type: \"move\",\r\n        t: (new Date().getTime() - startTime.getTime())\r\n    };\r\n    trackList.push(track);\r\n    if (moveX < 0) {\r\n        moveX = 0;\r\n    } else if (moveX > end) {\r\n        moveX = end;\r\n    }\r\n    currentCaptcha.currentCaptchaData.moveX = moveX;\r\n    currentCaptcha.currentCaptchaData.moveY = moveY;\r\n    if (currentCaptcha.doMove) {\r\n        currentCaptcha.doMove(event, currentCaptcha);\r\n    }\r\n    printLog([\"move\", track])\r\n}\r\nfunction destroyEvent(currentCaptcha) {\r\n    if (currentCaptcha) {\r\n        if (currentCaptcha.__m__) {\r\n            window.removeEventListener(\"mousemove\", currentCaptcha.__m__);\r\n            window.removeEventListener(\"touchmove\", currentCaptcha.__m__);\r\n        }\r\n        if (currentCaptcha.__u__) {\r\n            window.removeEventListener(\"mouseup\", currentCaptcha.__u__);\r\n            window.removeEventListener(\"touchend\", currentCaptcha.__u__);\r\n        }\r\n    }\r\n}\r\n\r\nfunction up(currentCaptcha, event) {\r\n    destroyEvent(currentCaptcha);\r\n    const coordinate = getCurrentCoordinate(event);\r\n    currentCaptcha.currentCaptchaData.stopTime = new Date();\r\n    const startTime = currentCaptcha.currentCaptchaData.startTime;\r\n    const trackList = currentCaptcha.currentCaptchaData.trackList;\r\n\r\n    const track = {\r\n        x: coordinate.x,\r\n        y: coordinate.y,\r\n        type: \"up\",\r\n        t: (new Date().getTime() - startTime.getTime())\r\n    }\r\n\r\n    trackList.push(track);\r\n    printLog([\"up\", track])\r\n    printLog([\"tracks\", trackList])\r\n    if (currentCaptcha.doUp) {\r\n        currentCaptcha.doUp(event, currentCaptcha)\r\n    }\r\n    currentCaptcha.endCallback(currentCaptcha.currentCaptchaData, currentCaptcha);\r\n}\r\n\r\nfunction initConfig(bgImageWidth, bgImageHeight, templateImageWidth, templateImageHeight, end) {\r\n    // bugfix 图片宽高可能会有小数情况，强转一下整数\r\n    const currentCaptchaConfig = {\r\n        startTime: new Date(),\r\n        trackList: [],\r\n        movePercent: 0,\r\n        clickCount: 0,\r\n        bgImageWidth: Math.round(bgImageWidth),\r\n        bgImageHeight: Math.round(bgImageHeight),\r\n        templateImageWidth: Math.round(templateImageWidth),\r\n        templateImageHeight: Math.round(templateImageHeight),\r\n        end: end\r\n    }\r\n    printLog([\"init\", currentCaptchaConfig]);\r\n    return currentCaptchaConfig;\r\n}\r\n\r\nfunction closeTips(el, callback) {\r\n    const tipEl = Dom(el).find(\"#tianai-captcha-tips\");\r\n    tipEl.removeClass(\"tianai-captcha-tips-on\")\r\n    // tipEl.removeClass(\"tianai-captcha-tips-success\")\r\n    // tipEl.removeClass(\"tianai-captcha-tips-error\")\r\n    // 延时\r\n    if (callback) {\r\n        setTimeout(callback, .35);\r\n    }\r\n}\r\n\r\nfunction showTips(el, msg, type, callback) {\r\n    const tipEl = Dom(el).find(\"#tianai-captcha-tips\");\r\n    tipEl.text(msg);\r\n    if (type === 1) {\r\n        // 成功\r\n        tipEl.removeClass(\"tianai-captcha-tips-error\")\r\n        tipEl.addClass(\"tianai-captcha-tips-success\")\r\n    } else {\r\n        // 失败\r\n        tipEl.removeClass(\"tianai-captcha-tips-success\")\r\n        tipEl.addClass(\"tianai-captcha-tips-error\")\r\n    }\r\n    tipEl.addClass(\"tianai-captcha-tips-on\");\r\n    // 延时\r\n    setTimeout(callback, 1000);\r\n}\r\n\r\nclass CommonCaptcha {\r\n    showTips(msg, type, callback) {\r\n        showTips(this.el, msg, type, callback)\r\n    }\r\n\r\n    closeTips(msg, callback) {\r\n        closeTips(this.el, msg, callback)\r\n    }\r\n}\r\n\r\nfunction Dom(domStr, dom) {\r\n    return new DomEl(domStr, dom);\r\n}\r\n\r\nclass DomEl {\r\n    constructor(domStr, dom) {\r\n        if (dom && typeof dom === 'object' && typeof dom.nodeType !== 'undefined') {\r\n            this.dom = dom;\r\n            this.domStr = domStr;\r\n            return;\r\n        }\r\n        if (domStr instanceof DomEl) {\r\n            this.dom = domStr.dom;\r\n            this.domStr = domStr.domStr;\r\n        } else if (typeof domStr === \"string\") {\r\n            this.dom = document.querySelector(domStr)\r\n            this.domStr = domStr;\r\n        } else if (typeof document === 'object' && typeof document.nodeType !== 'undefined') {\r\n            this.dom = domStr;\r\n            this.domStr = domStr.nodeName;\r\n        } else {\r\n            throw new Error(\"不支持的类型\");\r\n        }\r\n    }\r\n\r\n    each(callback) {\r\n        this.getTarget().querySelectorAll(\"*\").forEach(callback);\r\n    }\r\n\r\n    removeClass(className) {\r\n        let element = this.getTarget();\r\n        if (element.classList) {\r\n            // 使用 classList API 移除类\r\n            element.classList.remove(className);\r\n        } else {\r\n            // 兼容旧版本浏览器\r\n            const currentClass = element.className;\r\n            const regex = new RegExp('(?:^|\\\\s)' + className + '(?!\\\\S)', 'g');\r\n            element.className = currentClass.replace(regex, '');\r\n        }\r\n        return this;\r\n    }\r\n\r\n    addClass(className) {\r\n        const element = this.getTarget();\r\n        if (element.classList) {\r\n            // 使用 classList API 添加类\r\n            element.classList.add(className);\r\n        } else {\r\n            // 兼容旧版本浏览器\r\n            let currentClass = element.className;\r\n            if (currentClass.indexOf(className) === -1) {\r\n                element.className = currentClass + ' ' + className;\r\n            }\r\n        }\r\n        return this;\r\n    }\r\n\r\n    find(str) {\r\n        const el = this.getTarget().querySelector(str);\r\n        if (el) {\r\n            return new DomEl(str, el);\r\n        }\r\n        return null;\r\n    }\r\n\r\n    children(selector) {\r\n        const childNodes = this.getTarget().childNodes;\r\n        for (let i = 0; i < childNodes.length; i++) {\r\n            if (childNodes[i].nodeType === 1 && childNodes[i].matches(selector)) {\r\n                return new DomEl(selector, childNodes[i]);\r\n            }\r\n        }\r\n        return null;\r\n    }\r\n\r\n    remove() {\r\n        this.getTarget().remove();\r\n        return null;\r\n    }\r\n\r\n    css(property, value) {\r\n        if (typeof property === 'string' && typeof value === 'string') {\r\n            // 设置单个属性\r\n            this.getTarget().style[property] = value;\r\n        } else if (typeof property === 'object') {\r\n            // 设置多个属性\r\n            for (var prop in property) {\r\n                if (property.hasOwnProperty(prop)) {\r\n                    this.getTarget().style[prop] = property[prop];\r\n                }\r\n            }\r\n        } else if (typeof property === 'string' && typeof value === 'undefined') {\r\n            // 获取单个属性\r\n            return window.getComputedStyle(element)[property];\r\n        }\r\n    }\r\n\r\n    attr(attributeName, value) {\r\n        if (value === undefined) {\r\n            // 如果未提供值，则返回属性的当前值\r\n            return this.getTarget().getAttribute(attributeName);\r\n        } else {\r\n            // 如果提供了值，则设置属性的值\r\n            this.getTarget().setAttribute(attributeName, value);\r\n        }\r\n        return this;\r\n    }\r\n\r\n    text(str) {\r\n        this.getTarget().innerText = str;\r\n        return this;\r\n    }\r\n\r\n    html(str) {\r\n        this.getTarget().innerHtml = str;\r\n        return this;\r\n    }\r\n\r\n    is(dom) {\r\n        if (dom && typeof dom === 'object' && typeof dom.nodeType !== 'undefined') {\r\n            return this.dom === dom;\r\n        }\r\n        if (dom instanceof DomEl) {\r\n            return this.dom === dom.dom;\r\n        }\r\n    }\r\n\r\n    append(content) {\r\n        if (typeof content === 'string') {\r\n            this.getTarget().insertAdjacentHTML(\"beforeend\", content);\r\n        } else if (content instanceof HTMLElement) {\r\n            this.getTarget().appendChild(content);\r\n        } else {\r\n            throw new Error('Invalid content type');\r\n        }\r\n        return this;\r\n    }\r\n\r\n    click(fun) {\r\n        this.on(\"click\", fun);\r\n        return this;\r\n    }\r\n\r\n    mousedown(fun) {\r\n        this.on(\"mousedown\", fun);\r\n        return this;\r\n    }\r\n\r\n    touchstart(fun) {\r\n        this.on(\"touchstart\", fun);\r\n        return this;\r\n    }\r\n\r\n    on(eventType, fun) {\r\n        this.getTarget().addEventListener(eventType, fun, {passive: true});\r\n        return this;\r\n    }\r\n\r\n    width() {\r\n        return this.getTarget().offsetWidth;\r\n    }\r\n\r\n    height() {\r\n        return this.getTarget().offsetHeight;\r\n    }\r\n\r\n    getTarget() {\r\n        if (this.dom) {\r\n            return this.dom;\r\n        }\r\n        throw new Error(\"dom不存在: [\" + this.domStr + \"]\");\r\n    }\r\n}\r\n\r\nfunction http(options) {\r\n    return new Promise(function (resolve, reject) {\r\n        var xhr = new XMLHttpRequest();\r\n        xhr.open(options.method || 'GET', options.url);\r\n        // 设置请求头\r\n        if (options.headers) {\r\n            for (const header in options.headers) {\r\n                if (options.headers.hasOwnProperty(header)) {\r\n                    xhr.setRequestHeader(header, options.headers[header]);\r\n                }\r\n            }\r\n        }\r\n        xhr.onreadystatechange = function () {\r\n            if (xhr.readyState === XMLHttpRequest.DONE) {\r\n                if (xhr.status >= 200 && xhr.status <= 500) {\r\n                    const contentType = xhr.getResponseHeader('Content-Type');\r\n                    if (contentType && contentType.indexOf('application/json') !== -1) {\r\n                        resolve(JSON.parse(xhr.responseText));\r\n                    } else {\r\n                        resolve(xhr.responseText);\r\n                    }\r\n                } else {\r\n                    reject(new Error('Request failed with status: ' + xhr.status));\r\n                }\r\n            }\r\n        };\r\n        xhr.onerror = function () {\r\n            reject(new Error('Network Error'));\r\n        };\r\n        xhr.send(options.data);\r\n    });\r\n}\r\n\r\nfunction isEmptyObject(obj) {\r\n    for (var key in obj) {\r\n        if (obj.hasOwnProperty(key)) {\r\n            return false; // 对象不为空\r\n        }\r\n    }\r\n    return true; // 对象为空\r\n}\r\n\r\n\r\n\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/common/common.js?");

/***/ }),

/***/ "./src/captcha/concat/concat.js":
/*!**************************************!*\
  !*** ./src/captcha/concat/concat.js ***!
  \**************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony import */ var _common_common_scss__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ../common/common.scss */ \"./src/captcha/common/common.scss\");\n/* harmony import */ var _captcha_slider_slider_scss__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! @/captcha/slider/slider.scss */ \"./src/captcha/slider/slider.scss\");\n/* harmony import */ var _concat_scss__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./concat.scss */ \"./src/captcha/concat/concat.scss\");\n/* harmony import */ var _common_common_js__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! ../common/common.js */ \"./src/captcha/common/common.js\");\n\r\n\r\n\r\n\r\n\r\n\r\nconst TYPE = \"CONCAT\";\r\n\r\nfunction getTemplate(styleConfig) {\r\n    return `\r\n    <div id=\"tianai-captcha\" class=\"tianai-captcha-slider tianai-captcha-concat\">\r\n    <div class=\"slider-tip\">\r\n        <span id=\"tianai-captcha-slider-move-track-font\" >拖动滑块完成拼图</span>\r\n    </div>\r\n    <div class=\"content\">\r\n        <div class=\"tianai-captcha-slider-concat-img-div\" id=\"tianai-captcha-slider-concat-img-div\">\r\n            <img id=\"tianai-captcha-slider-concat-slider-img\" src=\"\" alt/>\r\n        </div>\r\n        <div class=\"tianai-captcha-slider-concat-bg-img\"></div>\r\n         <div class=\"tianai-captcha-tips\" id=\"tianai-captcha-tips\"></div>\r\n    </div>\r\n    <div class=\"slider-move\">\r\n        <div class=\"slider-move-track\">\r\n            <div id=\"tianai-captcha-slider-move-track-mask\"></div>\r\n            <div class=\"slider-move-shadow\"></div>\r\n        </div>\r\n        <div class=\"slider-move-btn\" id=\"tianai-captcha-slider-move-btn\">\r\n        </div>\r\n    </div>\r\n</div>\r\n    `;\r\n}\r\n\r\nclass Concat extends _common_common_js__WEBPACK_IMPORTED_MODULE_3__.CommonCaptcha {\r\n    constructor(divId, styleConfig) {\r\n        super();\r\n        this.boxEl = (0,_common_common_js__WEBPACK_IMPORTED_MODULE_3__.Dom)(divId);\r\n        this.styleConfig = styleConfig;\r\n        this.type = TYPE;\r\n        this.currentCaptchaData = {}\r\n    }\r\n\r\n    init(captchaData, endCallback, loadSuccessCallback) {\r\n        // 重载样式\r\n        this.destroy();\r\n        this.boxEl.append(getTemplate(this.styleConfig));\r\n        this.el = this.boxEl.find(\"#tianai-captcha\");\r\n        this.loadStyle();\r\n        // 按钮绑定事件\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").mousedown(_common_common_js__WEBPACK_IMPORTED_MODULE_3__.down.bind(null,this));\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").touchstart(_common_common_js__WEBPACK_IMPORTED_MODULE_3__.down.bind(null,this));\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_3__.clearAllPreventDefault)(this.el);\r\n        // 绑定全局\r\n        window.currentCaptcha = this;\r\n        // 载入验证码\r\n        this.loadCaptchaForData(this, captchaData);\r\n        this.endCallback = endCallback;\r\n        if (loadSuccessCallback) {\r\n            // 加载成功\r\n            loadSuccessCallback(this);\r\n        }\r\n        return this;\r\n    }\r\n\r\n    destroy() {\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_3__.destroyEvent)();\r\n        const existsCaptchaEl = this.boxEl.children(\"#tianai-captcha\");\r\n        if (existsCaptchaEl) {\r\n            existsCaptchaEl.remove();\r\n        }\r\n    }\r\n\r\n    doMove() {\r\n        const moveX = this.currentCaptchaData.moveX;\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").css(\"transform\", \"translate(\" + moveX + \"px, 0px)\")\r\n        this.el.find(\"#tianai-captcha-slider-concat-img-div\").css(\"background-position-x\", moveX + \"px\");\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"width\", moveX + \"px\")\r\n    }\r\n\r\n    loadStyle() {\r\n        let sliderImg = \"\";\r\n        let moveTrackMaskBorderColor = \"#00f4ab\";\r\n        let moveTrackMaskBgColor = \"#a9ffe5\";\r\n        const styleConfig = this.styleConfig;\r\n        if (styleConfig) {\r\n            sliderImg = styleConfig.btnUrl;\r\n            moveTrackMaskBgColor = styleConfig.moveTrackMaskBgColor;\r\n            moveTrackMaskBorderColor = styleConfig.moveTrackMaskBorderColor;\r\n        }\r\n        this.el.find(\".slider-move .slider-move-btn\").css(\"background-image\", \"url(\" + sliderImg + \")\");\r\n        // this.el.find(\"#tianai-captcha-slider-move-track-font\").text(title);\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"border-color\", moveTrackMaskBorderColor);\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"background-color\", moveTrackMaskBgColor);\r\n    }\r\n\r\n    loadCaptchaForData(that, data) {\r\n        const bgImg = that.el.find(\".tianai-captcha-slider-concat-bg-img\");\r\n        const sliderImg = that.el.find(\"#tianai-captcha-slider-concat-img-div\");\r\n        bgImg.css(\"background-image\", \"url(\" + data.data.backgroundImage + \")\");\r\n        sliderImg.css(\"background-image\", \"url(\" + data.data.backgroundImage + \")\");\r\n        sliderImg.css(\"background-position\", \"0px 0px\");\r\n        var backgroundImageHeight = data.data.backgroundImageHeight;\r\n        var height = ((backgroundImageHeight - data.data.data.randomY) / backgroundImageHeight) * 180;\r\n        sliderImg.css(\"height\", height+\"px\");\r\n\r\n        that.currentCaptchaData = (0,_common_common_js__WEBPACK_IMPORTED_MODULE_3__.initConfig)(bgImg.width(), bgImg.height(), sliderImg.width(), sliderImg.height(), 300 - 63 + 5);\r\n        that.currentCaptchaData.currentCaptchaId = data.data.id;\r\n    }\r\n}\r\n\r\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = (Concat);\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/concat/concat.js?");

/***/ }),

/***/ "./src/captcha/config/config.js":
/*!**************************************!*\
  !*** ./src/captcha/config/config.js ***!
  \**************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   CaptchaConfig: () => (/* binding */ CaptchaConfig),\n/* harmony export */   wrapConfig: () => (/* binding */ wrapConfig),\n/* harmony export */   wrapStyle: () => (/* binding */ wrapStyle)\n/* harmony export */ });\n/* harmony import */ var _styleConfig__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./styleConfig */ \"./src/captcha/config/styleConfig.js\");\n/* harmony import */ var _common_common__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ../common/common */ \"./src/captcha/common/common.js\");\n\r\n\r\nclass CaptchaConfig {\r\n    constructor(args) {\r\n        if (!args.bindEl) {\r\n            throw new Error(\"[TAC] 必须配置 [bindEl]用于将验证码绑定到该元素上\");\r\n        }\r\n        if (!args.requestCaptchaDataUrl) {\r\n            throw new Error(\"[TAC] 必须配置 [requestCaptchaDataUrl]请求验证码接口\");\r\n        }\r\n        if (!args.validCaptchaUrl) {\r\n            throw new Error(\"[TAC] 必须配置 [validCaptchaUrl]验证验证码接口\");\r\n        }\r\n        this.bindEl = args.bindEl;\r\n        this.domBindEl = (0,_common_common__WEBPACK_IMPORTED_MODULE_1__.Dom)(args.bindEl);\r\n        this.requestCaptchaDataUrl = args.requestCaptchaDataUrl;\r\n        this.validCaptchaUrl = args.validCaptchaUrl;\r\n        if (args.validSuccess) {\r\n            this.validSuccess = args.validSuccess;\r\n        }\r\n        if (args.validFail) {\r\n            this.validFail = args.validFail;\r\n        }\r\n        if (args.requestHeaders) {\r\n            this.requestHeaders = args.requestHeaders\r\n        }else {\r\n            this.requestHeaders = {}\r\n        }\r\n        if (args.btnCloseFun) {\r\n            this.btnCloseFun = args.btnCloseFun;\r\n        }\r\n        if (args.btnRefreshFun) {\r\n            this.btnRefreshFun = args.btnRefreshFun;\r\n        }\r\n        this.requestChain = [];\r\n        // 时间戳转换\r\n        this.timeToTimestamp = args.timeToTimestamp || true;\r\n        this.insertRequestChain(0, {\r\n            preRequest(type, param, c, tac) {\r\n                if (this.timeToTimestamp && param.data) {\r\n                    for (let key in param.data){\r\n                        // 将date全部转换为时间戳\r\n                        if (param.data[key] instanceof Date) {\r\n                            param.data[key] = param.data[key].getTime();\r\n                        }\r\n                    }\r\n                }\r\n                return true;\r\n            }\r\n        })\r\n    }\r\n    addRequestChain(fun) {\r\n        this.requestChain.push(fun);\r\n    }\r\n    insertRequestChain(index,chain) {\r\n        this.requestChain.splice(index, 0, chain);\r\n    }\r\n    removeRequestChain(index) {\r\n        this.requestChain.splice(index, 1);\r\n    }\r\n    requestCaptchaData() {\r\n        const requestParam = {}\r\n        requestParam.headers = this.requestHeaders || {};\r\n        requestParam.data = {};\r\n        // 设置默认值\r\n        requestParam.headers[\"Content-Type\"] = \"application/json;charset=UTF-8\";\r\n        requestParam.method=\"POST\";\r\n        requestParam.url = this.requestCaptchaDataUrl;\r\n        // 请求前装载参数\r\n        this._preRequest(\"requestCaptchaData\", requestParam);\r\n        // 发送请求\r\n        const request = this.doSendRequest(requestParam);\r\n        // 返回结果\r\n        return request.then(res => {\r\n            // 装返回结果\r\n            this._postRequest(\"requestCaptchaData\", requestParam, res);\r\n            // 返回结果\r\n            return res;\r\n        });\r\n    }\r\n\r\n    doSendRequest(requestParam) {\r\n        // 如果content-type是json，那么data就是json字符串, 这里直接匹配所有header是否包含application/json\r\n        if (requestParam.headers ) {\r\n            for (const key in requestParam.headers){\r\n                if(requestParam.headers[key].indexOf(\"application/json\") > -1) {\r\n                    if (typeof requestParam.data !== \"string\") {\r\n                        requestParam.data = JSON.stringify(requestParam.data);\r\n                    }\r\n                    break;\r\n                }\r\n            }\r\n        }\r\n        return (0,_common_common__WEBPACK_IMPORTED_MODULE_1__.http)(requestParam).then(res => {\r\n            try {\r\n                return JSON.parse(res);\r\n            }catch (e) {\r\n                return res;\r\n            }\r\n        })\r\n    }\r\n\r\n    _preRequest(type, requestParam, c, tac) {\r\n        for (let i = 0; i < this.requestChain.length; i++) {\r\n            const r = this.requestChain[i];\r\n            if (r.preRequest) {\r\n                if (!r.preRequest(type, requestParam, this, c, tac)) {\r\n                    break;\r\n                }\r\n            }\r\n        }\r\n\r\n    }\r\n\r\n    _postRequest(type, requestParam, res, c, tac) {\r\n        for (let i = 0; i < this.requestChain.length; i++) {\r\n            const r = this.requestChain[i];\r\n            // 判断r是否存圩postRequest方法\r\n            if (r.postRequest) {\r\n                if (!r.postRequest(type, requestParam, res, this, c, tac)) {\r\n                    break;\r\n                }\r\n            }\r\n        }\r\n    }\r\n\r\n    validCaptcha(currentCaptchaId, data, c, tac) {\r\n        const sendParam = {\r\n            id: currentCaptchaId,\r\n            data: data\r\n        };\r\n        let requestParam = {};\r\n        requestParam.headers = this.requestHeaders || {};\r\n        requestParam.data = sendParam;\r\n        requestParam.headers[\"Content-Type\"] = \"application/json;charset=UTF-8\";\r\n        requestParam.method=\"POST\";\r\n        requestParam.url = this.validCaptchaUrl;\r\n\r\n        this._preRequest(\"validCaptcha\", requestParam, c, tac);\r\n        const request = this.doSendRequest(requestParam);\r\n        return request.then(res => {\r\n            this._postRequest(\"validCaptcha\", requestParam, res, c, tac);\r\n            return res;\r\n        }).then(res => {\r\n            if (res.code == 200) {\r\n                const useTimes = (data.stopTime - data.startTime) / 1000;\r\n                c.showTips(`验证成功,耗时${useTimes}秒`, 1, () => this.validSuccess(res, c, tac));\r\n            } else {\r\n                let tipMsg = \"验证失败，请重新尝试!\";\r\n                if (res.code) {\r\n                    if (res.code != 4001) {\r\n                        tipMsg = \"验证码被黑洞吸走了！\";\r\n                    }\r\n                }\r\n                c.showTips(tipMsg, 0, () => this.validFail(res, c, tac));\r\n            }\r\n        }).catch(e => {\r\n            let tipMsg = c.styleConfig.i18n.tips_error;\r\n            if (e.code && e.code != 200) {\r\n                if (res.code != 4001) {\r\n                    tipMsg = c.styleConfig.i18n.tips_4001;\r\n                }\r\n                c.showTips(tipMsg, 0, () => this.validFail(res, c, tac));\r\n            }\r\n        })\r\n\r\n    }\r\n\r\n    validSuccess(res, c, tac) {\r\n        console.log(\"验证码校验成功， 请重写  [config.validSuccess] 方法， 用于自定义逻辑处理\")\r\n        window.currentCaptchaRes = res;\r\n        tac.destroyWindow();\r\n    }\r\n\r\n    validFail(res, c, tac) {\r\n        tac.reloadCaptcha();\r\n    }\r\n}\r\n\r\nfunction wrapConfig(config) {\r\n    if (config instanceof CaptchaConfig) {\r\n        return config;\r\n    }\r\n    return new CaptchaConfig(config);\r\n}\r\n\r\nfunction wrapStyle(style) {\r\n    // if (!style) {\r\n    //     style = {}\r\n    // }\r\n    //\r\n    // if (!style.btnUrl) {\r\n    //     // 设置默认图片\r\n    //     style.btnUrl = \"data:image/png;base64,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\";\r\n    // }\r\n    // if (!style.moveTrackMaskBgColor && !style.moveTrackMaskBorderColor) {\r\n    //     style.moveTrackMaskBgColor = \"#89d2ff\";\r\n    //     style.moveTrackMaskBorderColor = \"#0298f8\";\r\n    //\r\n    // }\r\n    // return style;\r\n\r\n    let margeStyle = {..._styleConfig__WEBPACK_IMPORTED_MODULE_0__[\"default\"], ...style};\r\n    margeStyle.i18n = {..._styleConfig__WEBPACK_IMPORTED_MODULE_0__[\"default\"].i18n, ...style?.i18n};\r\n    return margeStyle;\r\n}\r\n\r\nconst captchaRequestChains = {}\r\n\r\n\r\n\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/config/config.js?");

/***/ }),

/***/ "./src/captcha/config/styleConfig.js":
/*!*******************************************!*\
  !*** ./src/captcha/config/styleConfig.js ***!
  \*******************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = ({\r\n    // 按钮图片\r\n    btnUrl: \"data:image/png;base64,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\",\r\n    // 移动边框背景颜色\r\n    moveTrackMaskBgColor: \"#89d2ff\",\r\n    // 移动边框颜色\r\n    moveTrackMaskBorderColor: \"#0298f8\",\r\n    // 文字提示\r\n    i18n: {\r\n        tips_success: \"验证成功,耗时%s秒\",\r\n        tips_error : \"验证失败，请重新尝试!\",\r\n        slider_title:\"拖动滑块完成拼图\",\r\n        concat_title: \"拖动滑块完成拼图\",\r\n        image_click_title: \"请依次点击:\",\r\n        rotate_title: \"拖动滑块完成拼图\",\r\n        // TITLE 大小\r\n        slider_title_size:\"15px\",\r\n        concat_title_size: \"15px\",\r\n        image_click_title_size: \"20px\",\r\n        rotate_title_size: \"15px\",\r\n    }\r\n\r\n});\r\n\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/config/styleConfig.js?");

/***/ }),

/***/ "./src/captcha/disable/disable.js":
/*!****************************************!*\
  !*** ./src/captcha/disable/disable.js ***!
  \****************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony import */ var _disable_scss__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./disable.scss */ \"./src/captcha/disable/disable.scss\");\n/* harmony import */ var _common_common__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ../common/common */ \"./src/captcha/common/common.js\");\nconst TYPE = \"DISABLE\";\r\n\r\n\r\n\r\nfunction getTemplate(styleConfig) {\r\n   return `\r\n    <div id=\"tianai-captcha\" class=\"tianai-captcha-disable\">\r\n        <div class=\"slider-tip\">\r\n            <span id=\"tianai-captcha-slider-move-track-font\" style=\"font-size: ${styleConfig.i18n.disable_title_size}\">${styleConfig.i18n.disable_title}</span>\r\n        </div>\r\n        <div class=\"content\">\r\n           <div class=\"bg-img-div\">\r\n<!--                <svg width=\"100\" height=\"100\" viewBox=\"0 0 100 100\">-->\r\n<!--                  <polygon points=\"50,10 90,90 10,90\" fill=\"none\" stroke=\"#FF9900\" stroke-width=\"4\"/>-->\r\n<!--                  <path d=\"M50 35V65 M50 75V75\" stroke=\"#FF9900\" stroke-width=\"4\" stroke-linecap=\"round\"/>-->\r\n<!--                </svg>-->\r\n                <span id=\"content-span\"></span>\r\n            </div>\r\n        </div>\r\n    </div>\r\n    `;\r\n}\r\nclass Disable {\r\n    constructor(boxEl, styleConfig) {\r\n        this.boxEl = boxEl;\r\n        this.styleConfig = styleConfig;\r\n        this.type = TYPE;\r\n        this.currentCaptchaData = {}\r\n    }\r\n    init(captchaData, endCallback, loadSuccessCallback) {\r\n        // 重载样式\r\n        this.destroy();\r\n        this.boxEl.append(getTemplate(this.styleConfig));\r\n        this.el = this.boxEl.find(\"#tianai-captcha\");\r\n        // 绑定全局\r\n        // window.currentCaptcha = this;\r\n        // 载入验证码\r\n        this.loadCaptchaForData(this, captchaData);\r\n        this.endCallback = endCallback;\r\n        if (loadSuccessCallback) {\r\n            // 加载成功\r\n            loadSuccessCallback(this);\r\n        }\r\n        return this;\r\n    }\r\n\r\n    destroy () {\r\n        const existsCaptchaEl = this.boxEl.find(\"#tianai-captcha\");\r\n        if (existsCaptchaEl) {\r\n            existsCaptchaEl.remove();\r\n        }\r\n    }\r\n    loadCaptchaForData (that, data) {\r\n        const msg = data.msg || data.message || \"接口异常\";\r\n        that.el.find(\"#content-span\").text(msg);\r\n    }\r\n}\r\n\r\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = (Disable);\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/disable/disable.js?");

/***/ }),

/***/ "./src/captcha/image_click/image_click.js":
/*!************************************************!*\
  !*** ./src/captcha/image_click/image_click.js ***!
  \************************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony import */ var _image_click_scss__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./image_click.scss */ \"./src/captcha/image_click/image_click.scss\");\n/* harmony import */ var _common_common_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ../common/common.js */ \"./src/captcha/common/common.js\");\n\r\n\r\n\r\n/**\r\n * 滑动验证码\r\n */\r\n\r\nconst TYPE = \"IMAGE_CLICK\"\r\nfunction getTemplate(styleConfig) {\r\n    return `\r\n<div id=\"tianai-captcha\" class=\"tianai-captcha-slider tianai-captcha-word-click\">\r\n    <div class=\"click-tip\">\r\n        <span id=\"tianai-captcha-click-track-font\" style=\"font-size: ${styleConfig.i18n.image_click_title_size}\">${styleConfig.i18n.image_click_title}</span>\r\n        <img src=\"\" id=\"tianai-captcha-tip-img\" class=\"tip-img\">\r\n    </div>\r\n    <div class=\"content\">\r\n        <div class=\"bg-img-div\">\r\n            <img id=\"tianai-captcha-slider-bg-img\" src=\"\" alt/>\r\n            <canvas id=\"tianai-captcha-slider-bg-canvas\"></canvas>\r\n            <div id=\"bg-img-click-mask\"></div>\r\n        </div>\r\n         <div class=\"tianai-captcha-tips\" id=\"tianai-captcha-tips\"></div>\r\n    </div>\r\n    <div class=\"click-confirm-btn\">确定</div>\r\n</div>\r\n`;\r\n}\r\nclass ImageClick extends _common_common_js__WEBPACK_IMPORTED_MODULE_1__.CommonCaptcha{\r\n    constructor(boxEl, styleConfig) {\r\n        super();\r\n        this.boxEl = boxEl;\r\n        this.styleConfig = styleConfig;\r\n        this.type = TYPE;\r\n        this.currentCaptchaData = {}\r\n    }\r\n    init(captchaData, endCallback, loadSuccessCallback) {\r\n        // 重载样式\r\n        this.destroy();\r\n        this.boxEl.append(getTemplate(this.styleConfig));\r\n        this.el = this.boxEl.find(\"#tianai-captcha\");\r\n        // 绑定全局\r\n        // window.currentCaptcha = this;\r\n        // 载入验证码\r\n        this.loadCaptchaForData(this, captchaData);\r\n        this.endCallback = endCallback;\r\n        const moveFun = _common_common_js__WEBPACK_IMPORTED_MODULE_1__.move.bind(null, this);\r\n        // 绑定事件\r\n        this.el.find(\"#bg-img-click-mask\").click((event) => {\r\n            if(event.target.className === \"click-span\") {\r\n\r\n                return;\r\n            }\r\n            this.currentCaptchaData.clickCount++;\r\n            const trackList = this.currentCaptchaData.trackList;\r\n            if (this.currentCaptchaData.clickCount === 1) {\r\n                this.currentCaptchaData.startTime = new Date();\r\n                // move 轨迹\r\n                window.addEventListener(\"mousemove\", moveFun);\r\n                this.currentCaptchaData.startX = event.offsetX;\r\n                this.currentCaptchaData.startY = event.offsetY;\r\n            }\r\n            const startTime = this.currentCaptchaData.startTime;\r\n            trackList.push({\r\n                x: Math.round(event.offsetX),\r\n                y: Math.round(event.offsetY),\r\n                type: \"click\",\r\n                t: (new Date().getTime() - startTime.getTime())\r\n            });\r\n            const left = event.offsetX - 10;\r\n            const top = event.offsetY - 10;\r\n            this.el.find(\"#bg-img-click-mask\").append(\"<span class='click-span' style='left:\" + left + \"px;top: \" + top + \"px'>\" + this.currentCaptchaData.clickCount + \"</span>\")\r\n            // if (this.currentCaptchaData.clickCount === 4) {\r\n            //     // 校验\r\n            //     this.currentCaptchaData.stopTime = new Date();\r\n            //     window.removeEventListener(\"mousemove\", move);\r\n            //     this.endCallback(this.currentCaptchaData,this);\r\n            // }\r\n        });\r\n        this.el.find(\".click-confirm-btn\").click(() => {\r\n\r\n            if (this.currentCaptchaData.clickCount > 0) {\r\n                // 校验\r\n                this.currentCaptchaData.stopTime = new Date();\r\n                window.removeEventListener(\"mousemove\", moveFun);\r\n                this.endCallback(this.currentCaptchaData,this);\r\n            }\r\n        });\r\n\r\n        if (loadSuccessCallback) {\r\n            // 加载成功\r\n            loadSuccessCallback(this);\r\n        }\r\n        return this;\r\n    }\r\n    destroy () {\r\n        const existsCaptchaEl = this.boxEl.children(\"#tianai-captcha\");\r\n        if (existsCaptchaEl) {\r\n            existsCaptchaEl.remove();\r\n        }\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_1__.destroyEvent)();\r\n    }\r\n    loadCaptchaForData (that, data) {\r\n        const bgImg = that.el.find(\"#tianai-captcha-slider-bg-img\");\r\n        const tipImg = that.el.find(\"#tianai-captcha-tip-img\");\r\n        bgImg.on(\"load\",() => {\r\n            that.currentCaptchaData = (0,_common_common_js__WEBPACK_IMPORTED_MODULE_1__.initConfig)(bgImg.width(), bgImg.height(), tipImg.width(), tipImg.height());\r\n            that.currentCaptchaData.currentCaptchaId = data.data.id;\r\n        })\r\n        bgImg.attr(\"src\", data.data.backgroundImage);\r\n        tipImg.attr(\"src\", data.data.templateImage);\r\n    }\r\n}\r\n\r\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = (ImageClick);\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/image_click/image_click.js?");

/***/ }),

/***/ "./src/captcha/rotate/rotate.js":
/*!**************************************!*\
  !*** ./src/captcha/rotate/rotate.js ***!
  \**************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony import */ var _captcha_slider_slider_scss__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! @/captcha/slider/slider.scss */ \"./src/captcha/slider/slider.scss\");\n/* harmony import */ var _rotate_scss__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./rotate.scss */ \"./src/captcha/rotate/rotate.scss\");\n/* harmony import */ var _common_common_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ../common/common.js */ \"./src/captcha/common/common.js\");\n\r\n\r\n\r\n\r\n/**\r\n * 滑动验证码\r\n */\r\n\r\nconst TYPE = \"ROTATE\"\r\nfunction getTemplate(styleConfig) {\r\n    return `\r\n<div id=\"tianai-captcha\" class=\"tianai-captcha-slider tianai-captcha-rotate\">\r\n    <div class=\"slider-tip\">\r\n        <span id=\"tianai-captcha-slider-move-track-font\" style=\"font-size: ${styleConfig.i18n.rotate_title_size}\">${styleConfig.i18n.rotate_title}</span>\r\n    </div>\r\n    <div class=\"content\">\r\n        <div class=\"bg-img-div\">\r\n            <img id=\"tianai-captcha-slider-bg-img\" src=\"\" alt/>\r\n            <canvas id=\"tianai-captcha-slider-bg-canvas\"></canvas>\r\n        </div>\r\n        <div class=\"rotate-img-div\" id=\"tianai-captcha-slider-img-div\">\r\n            <img id=\"tianai-captcha-slider-move-img\" src=\"\" alt/>\r\n        </div>\r\n         <div class=\"tianai-captcha-tips\" id=\"tianai-captcha-tips\"></div>\r\n    </div>\r\n    <div class=\"slider-move\">\r\n        <div class=\"slider-move-track\">\r\n            <div id=\"tianai-captcha-slider-move-track-mask\"></div>\r\n            <div class=\"slider-move-shadow\"></div>\r\n        </div>\r\n        <div class=\"slider-move-btn\" id=\"tianai-captcha-slider-move-btn\">\r\n        </div>\r\n    </div>\r\n</div>\r\n`;\r\n}\r\nclass Rotate extends _common_common_js__WEBPACK_IMPORTED_MODULE_2__.CommonCaptcha{\r\n    constructor(boxEl, styleConfig) {\r\n        super();\r\n        this.boxEl = boxEl;\r\n        this.styleConfig = styleConfig;\r\n        this.type = TYPE;\r\n        this.currentCaptchaData = {}\r\n    }\r\n    init(captchaData, endCallback, loadSuccessCallback) {\r\n        // 重载样式\r\n        this.destroy();\r\n        this.boxEl.append(getTemplate(this.styleConfig));\r\n        this.el = this.boxEl.find(\"#tianai-captcha\");\r\n        this.loadStyle();\r\n        // 按钮绑定事件\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").mousedown(_common_common_js__WEBPACK_IMPORTED_MODULE_2__.down.bind(null,this));\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").touchstart(_common_common_js__WEBPACK_IMPORTED_MODULE_2__.down.bind(null,this));\r\n        // 绑定全局\r\n        // window.currentCaptcha = this;\r\n        // 载入验证码\r\n        this.loadCaptchaForData(this, captchaData);\r\n        this.endCallback = endCallback;\r\n        if (loadSuccessCallback) {\r\n            // 加载成功\r\n            loadSuccessCallback(this);\r\n        }\r\n        return this;\r\n    }\r\n\r\n    destroy () {\r\n        const existsCaptchaEl = this.boxEl.children(\"#tianai-captcha\");\r\n        if (existsCaptchaEl) {\r\n            existsCaptchaEl.remove();\r\n        }\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_2__.destroyEvent)();\r\n    }\r\n    doMove() {\r\n        const moveX = this.currentCaptchaData.moveX;\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").css(\"transform\", \"translate(\" + moveX + \"px, 0px)\")\r\n        this.el.find(\"#tianai-captcha-slider-move-img\").css(\"transform\", \"rotate(\" + (moveX / (this.currentCaptchaData.end / 360)) + \"deg)\")\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"width\", moveX + \"px\")\r\n    }\r\n    loadStyle () {\r\n        let sliderImg = \"\";\r\n        let moveTrackMaskBorderColor = \"#00f4ab\";\r\n        let moveTrackMaskBgColor = \"#a9ffe5\";\r\n        const styleConfig = this.styleConfig;\r\n        if (styleConfig) {\r\n            sliderImg = styleConfig.btnUrl;\r\n            moveTrackMaskBgColor = styleConfig.moveTrackMaskBgColor;\r\n            moveTrackMaskBorderColor = styleConfig.moveTrackMaskBorderColor;\r\n        }\r\n        this.el.find(\".slider-move .slider-move-btn\").css(\"background-image\", \"url(\" + sliderImg + \")\");\r\n        // this.el.find(\"#tianai-captcha-slider-move-track-font\").text(title);\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"border-color\", moveTrackMaskBorderColor);\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"background-color\", moveTrackMaskBgColor);\r\n    }\r\n    loadCaptchaForData (that, data) {\r\n        const bgImg = that.el.find(\"#tianai-captcha-slider-bg-img\");\r\n        const sliderImg = that.el.find(\"#tianai-captcha-slider-move-img\");\r\n        bgImg.attr(\"src\", data.data.backgroundImage);\r\n        sliderImg.attr(\"src\", data.data.templateImage);\r\n        bgImg.on(\"load\",() => {\r\n            that.currentCaptchaData = (0,_common_common_js__WEBPACK_IMPORTED_MODULE_2__.initConfig)(bgImg.width(), bgImg.height(), sliderImg.width(), sliderImg.height(), 300 - 63 + 5);\r\n            that.currentCaptchaData.currentCaptchaId = data.data.id;\r\n        });\r\n    }\r\n}\r\n\r\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = (Rotate);\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/rotate/rotate.js?");

/***/ }),

/***/ "./src/captcha/slider/slider.js":
/*!**************************************!*\
  !*** ./src/captcha/slider/slider.js ***!
  \**************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony import */ var _common_common_scss__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ../common/common.scss */ \"./src/captcha/common/common.scss\");\n/* harmony import */ var _slider_scss__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./slider.scss */ \"./src/captcha/slider/slider.scss\");\n/* harmony import */ var _common_common_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ../common/common.js */ \"./src/captcha/common/common.js\");\n\r\n\r\n\r\n\r\n/**\r\n * 滑动验证码\r\n */\r\n\r\nconst TYPE = \"SLIDER\"\r\nfunction getTemplate(styleConfig) {\r\n    return `\r\n<div id=\"tianai-captcha\" class=\"tianai-captcha-slider\">\r\n    <div class=\"slider-tip\">\r\n        <span id=\"tianai-captcha-slider-move-track-font\" style=\"font-size: ${styleConfig.i18n.slider_title_size}\">${styleConfig.i18n.slider_title}</span>\r\n    </div>\r\n    <div class=\"content\">\r\n        <div class=\"bg-img-div\">\r\n            <img id=\"tianai-captcha-slider-bg-img\" src=\"\" alt/>\r\n            <canvas id=\"tianai-captcha-slider-bg-canvas\"></canvas>\r\n            <div id=\"tianai-captcha-slider-bg-div\"></div>\r\n        </div>\r\n        <div class=\"slider-img-div\" id=\"tianai-captcha-slider-img-div\">\r\n            <img id=\"tianai-captcha-slider-move-img\" src=\"\" alt/>\r\n        </div>\r\n        <div class=\"tianai-captcha-tips\" id=\"tianai-captcha-tips\"></div>\r\n    </div>\r\n    <div class=\"slider-move\">\r\n        <div class=\"slider-move-track\">\r\n            <div id=\"tianai-captcha-slider-move-track-mask\"></div>\r\n            <div class=\"slider-move-shadow\"></div>\r\n        </div>\r\n        <div class=\"slider-move-btn\" id=\"tianai-captcha-slider-move-btn\">\r\n        </div>\r\n    </div>\r\n\r\n</div>\r\n`\r\n}\r\n\r\nclass Slider extends _common_common_js__WEBPACK_IMPORTED_MODULE_2__.CommonCaptcha{\r\n    constructor(boxEl, styleConfig) {\r\n        super();\r\n        this.boxEl = boxEl;\r\n        this.styleConfig = styleConfig;\r\n        this.type = TYPE;\r\n        this.currentCaptchaData = {}\r\n    }\r\n    init(captchaData, endCallback, loadSuccessCallback) {\r\n        // 重载样式\r\n        this.destroy();\r\n        this.boxEl.append(getTemplate(this.styleConfig));\r\n        this.el = this.boxEl.find(\"#tianai-captcha\");\r\n        this.loadStyle();\r\n        // 按钮绑定事件\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").mousedown(_common_common_js__WEBPACK_IMPORTED_MODULE_2__.down.bind(null,this));\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").touchstart( _common_common_js__WEBPACK_IMPORTED_MODULE_2__.down.bind(null,this));\r\n        // 绑定全局\r\n        // window.currentCaptcha = this;\r\n        // 载入验证码\r\n        this.loadCaptchaForData(this, captchaData);\r\n        this.endCallback = endCallback;\r\n        if (loadSuccessCallback) {\r\n            // 加载成功\r\n            loadSuccessCallback(this);\r\n        }\r\n        return this;\r\n    }\r\n    showTips(msg, type,callback) {\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_2__.showTips)(this.el, msg,type, callback)\r\n    }\r\n    closeTips(callback) {\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_2__.closeTips)(this.el, callback)\r\n    }\r\n\r\n    destroy () {\r\n        const existsCaptchaEl = this.boxEl.children(\"#tianai-captcha\");\r\n        if (existsCaptchaEl) {\r\n            existsCaptchaEl.remove();\r\n        }\r\n        (0,_common_common_js__WEBPACK_IMPORTED_MODULE_2__.destroyEvent)();\r\n    }\r\n    doMove() {\r\n        const moveX = this.currentCaptchaData.moveX;\r\n        this.el.find(\"#tianai-captcha-slider-move-btn\").css(\"transform\", \"translate(\" + moveX + \"px, 0px)\")\r\n        this.el.find(\"#tianai-captcha-slider-img-div\").css(\"transform\", \"translate(\" + moveX + \"px, 0px)\")\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"width\", moveX + \"px\")\r\n    }\r\n    loadStyle () {\r\n        let sliderImg = \"\";\r\n        let moveTrackMaskBorderColor = \"#00f4ab\";\r\n        let moveTrackMaskBgColor = \"#a9ffe5\";\r\n        const styleConfig = this.styleConfig;\r\n        if (styleConfig) {\r\n            sliderImg = styleConfig.btnUrl;\r\n            moveTrackMaskBgColor = styleConfig.moveTrackMaskBgColor;\r\n            moveTrackMaskBorderColor = styleConfig.moveTrackMaskBorderColor;\r\n        }\r\n        this.el.find(\".slider-move .slider-move-btn\").css(\"background-image\", \"url(\" + sliderImg + \")\");\r\n        // this.el.find(\"#tianai-captcha-slider-move-track-font\").text(title);\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"border-color\", moveTrackMaskBorderColor);\r\n        this.el.find(\"#tianai-captcha-slider-move-track-mask\").css(\"background-color\", moveTrackMaskBgColor);\r\n    }\r\n    loadCaptchaForData (that, data) {\r\n        const bgImg = that.el.find(\"#tianai-captcha-slider-bg-img\");\r\n        const sliderImg = that.el.find(\"#tianai-captcha-slider-move-img\");\r\n        bgImg.attr(\"src\", data.data.backgroundImage);\r\n        sliderImg.attr(\"src\", data.data.templateImage);\r\n        bgImg.on(\"load\",() => {\r\n            that.currentCaptchaData = (0,_common_common_js__WEBPACK_IMPORTED_MODULE_2__.initConfig)(bgImg.width(), bgImg.height(), sliderImg.width(), sliderImg.height(), 300 - 63 + 5);\r\n            that.currentCaptchaData.currentCaptchaId = data.data.id;\r\n        });\r\n    }\r\n}\r\n\r\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = (Slider);\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/slider/slider.js?");

/***/ }),

/***/ "./src/captcha/word_image_click/word_image_click.js":
/*!**********************************************************!*\
  !*** ./src/captcha/word_image_click/word_image_click.js ***!
  \**********************************************************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony export */ __webpack_require__.d(__webpack_exports__, {\n/* harmony export */   \"default\": () => (__WEBPACK_DEFAULT_EXPORT__)\n/* harmony export */ });\n/* harmony import */ var _image_click_image_click__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ../image_click/image_click */ \"./src/captcha/image_click/image_click.js\");\n\r\n/**\r\n * 滑动验证码\r\n */\r\nconst TYPE = \"WORD_IMAGE_CLICK\"\r\nclass WordImageClick extends _image_click_image_click__WEBPACK_IMPORTED_MODULE_0__[\"default\"] {\r\n    constructor(divId, styleConfig) {\r\n        super(divId, styleConfig);\r\n        this.type = TYPE;\r\n    }\r\n}\r\n\r\n/* harmony default export */ const __WEBPACK_DEFAULT_EXPORT__ = (WordImageClick);\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/captcha/word_image_click/word_image_click.js?");

/***/ }),

/***/ "./src/index.js":
/*!**********************!*\
  !*** ./src/index.js ***!
  \**********************/
/***/ ((__unused_webpack_module, __webpack_exports__, __webpack_require__) => {

eval("__webpack_require__.r(__webpack_exports__);\n/* harmony import */ var _captcha_captcha__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./captcha/captcha */ \"./src/captcha/captcha.js\");\n\r\n\r\nwindow.TAC = _captcha_captcha__WEBPACK_IMPORTED_MODULE_0__.TianAiCaptcha;\r\nwindow.CaptchaConfig = _captcha_captcha__WEBPACK_IMPORTED_MODULE_0__.CaptchaConfig;\r\n\n\n//# sourceURL=webpack://webpack-demo/./src/index.js?");

/***/ })

/******/ 	});
/************************************************************************/
/******/ 	// The module cache
/******/ 	var __webpack_module_cache__ = {};
/******/ 	
/******/ 	// The require function
/******/ 	function __webpack_require__(moduleId) {
/******/ 		// Check if module is in cache
/******/ 		var cachedModule = __webpack_module_cache__[moduleId];
/******/ 		if (cachedModule !== undefined) {
/******/ 			if (cachedModule.error !== undefined) throw cachedModule.error;
/******/ 			return cachedModule.exports;
/******/ 		}
/******/ 		// Create a new module (and put it into the cache)
/******/ 		var module = __webpack_module_cache__[moduleId] = {
/******/ 			id: moduleId,
/******/ 			// no module.loaded needed
/******/ 			exports: {}
/******/ 		};
/******/ 	
/******/ 		// Execute the module function
/******/ 		try {
/******/ 			var execOptions = { id: moduleId, module: module, factory: __webpack_modules__[moduleId], require: __webpack_require__ };
/******/ 			__webpack_require__.i.forEach(function(handler) { handler(execOptions); });
/******/ 			module = execOptions.module;
/******/ 			execOptions.factory.call(module.exports, module, module.exports, execOptions.require);
/******/ 		} catch(e) {
/******/ 			module.error = e;
/******/ 			throw e;
/******/ 		}
/******/ 	
/******/ 		// Return the exports of the module
/******/ 		return module.exports;
/******/ 	}
/******/ 	
/******/ 	// expose the modules object (__webpack_modules__)
/******/ 	__webpack_require__.m = __webpack_modules__;
/******/ 	
/******/ 	// expose the module cache
/******/ 	__webpack_require__.c = __webpack_module_cache__;
/******/ 	
/******/ 	// expose the module execution interceptor
/******/ 	__webpack_require__.i = [];
/******/ 	
/************************************************************************/
/******/ 	/* webpack/runtime/define property getters */
/******/ 	(() => {
/******/ 		// define getter functions for harmony exports
/******/ 		__webpack_require__.d = (exports, definition) => {
/******/ 			for(var key in definition) {
/******/ 				if(__webpack_require__.o(definition, key) && !__webpack_require__.o(exports, key)) {
/******/ 					Object.defineProperty(exports, key, { enumerable: true, get: definition[key] });
/******/ 				}
/******/ 			}
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/get javascript update chunk filename */
/******/ 	(() => {
/******/ 		// This function allow to reference all chunks
/******/ 		__webpack_require__.hu = (chunkId) => {
/******/ 			// return url for filenames based on template
/******/ 			return "" + chunkId + "." + __webpack_require__.h() + ".hot-update.js";
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/get mini-css chunk filename */
/******/ 	(() => {
/******/ 		// This function allow to reference async chunks
/******/ 		__webpack_require__.miniCssF = (chunkId) => {
/******/ 			// return url for filenames based on template
/******/ 			return undefined;
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/get update manifest filename */
/******/ 	(() => {
/******/ 		__webpack_require__.hmrF = () => ("main." + __webpack_require__.h() + ".hot-update.json");
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/getFullHash */
/******/ 	(() => {
/******/ 		__webpack_require__.h = () => ("4d94e5b0413e21444671")
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/global */
/******/ 	(() => {
/******/ 		__webpack_require__.g = (function() {
/******/ 			if (typeof globalThis === 'object') return globalThis;
/******/ 			try {
/******/ 				return this || new Function('return this')();
/******/ 			} catch (e) {
/******/ 				if (typeof window === 'object') return window;
/******/ 			}
/******/ 		})();
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/hasOwnProperty shorthand */
/******/ 	(() => {
/******/ 		__webpack_require__.o = (obj, prop) => (Object.prototype.hasOwnProperty.call(obj, prop))
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/load script */
/******/ 	(() => {
/******/ 		var inProgress = {};
/******/ 		var dataWebpackPrefix = "webpack-demo:";
/******/ 		// loadScript function to load a script via script tag
/******/ 		__webpack_require__.l = (url, done, key, chunkId) => {
/******/ 			if(inProgress[url]) { inProgress[url].push(done); return; }
/******/ 			var script, needAttach;
/******/ 			if(key !== undefined) {
/******/ 				var scripts = document.getElementsByTagName("script");
/******/ 				for(var i = 0; i < scripts.length; i++) {
/******/ 					var s = scripts[i];
/******/ 					if(s.getAttribute("src") == url || s.getAttribute("data-webpack") == dataWebpackPrefix + key) { script = s; break; }
/******/ 				}
/******/ 			}
/******/ 			if(!script) {
/******/ 				needAttach = true;
/******/ 				script = document.createElement('script');
/******/ 		
/******/ 				script.charset = 'utf-8';
/******/ 				script.timeout = 120;
/******/ 				if (__webpack_require__.nc) {
/******/ 					script.setAttribute("nonce", __webpack_require__.nc);
/******/ 				}
/******/ 				script.setAttribute("data-webpack", dataWebpackPrefix + key);
/******/ 		
/******/ 				script.src = url;
/******/ 			}
/******/ 			inProgress[url] = [done];
/******/ 			var onScriptComplete = (prev, event) => {
/******/ 				// avoid mem leaks in IE.
/******/ 				script.onerror = script.onload = null;
/******/ 				clearTimeout(timeout);
/******/ 				var doneFns = inProgress[url];
/******/ 				delete inProgress[url];
/******/ 				script.parentNode && script.parentNode.removeChild(script);
/******/ 				doneFns && doneFns.forEach((fn) => (fn(event)));
/******/ 				if(prev) return prev(event);
/******/ 			}
/******/ 			var timeout = setTimeout(onScriptComplete.bind(null, undefined, { type: 'timeout', target: script }), 120000);
/******/ 			script.onerror = onScriptComplete.bind(null, script.onerror);
/******/ 			script.onload = onScriptComplete.bind(null, script.onload);
/******/ 			needAttach && document.head.appendChild(script);
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/make namespace object */
/******/ 	(() => {
/******/ 		// define __esModule on exports
/******/ 		__webpack_require__.r = (exports) => {
/******/ 			if(typeof Symbol !== 'undefined' && Symbol.toStringTag) {
/******/ 				Object.defineProperty(exports, Symbol.toStringTag, { value: 'Module' });
/******/ 			}
/******/ 			Object.defineProperty(exports, '__esModule', { value: true });
/******/ 		};
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/hot module replacement */
/******/ 	(() => {
/******/ 		var currentModuleData = {};
/******/ 		var installedModules = __webpack_require__.c;
/******/ 		
/******/ 		// module and require creation
/******/ 		var currentChildModule;
/******/ 		var currentParents = [];
/******/ 		
/******/ 		// status
/******/ 		var registeredStatusHandlers = [];
/******/ 		var currentStatus = "idle";
/******/ 		
/******/ 		// while downloading
/******/ 		var blockingPromises = 0;
/******/ 		var blockingPromisesWaiting = [];
/******/ 		
/******/ 		// The update info
/******/ 		var currentUpdateApplyHandlers;
/******/ 		var queuedInvalidatedModules;
/******/ 		
/******/ 		// eslint-disable-next-line no-unused-vars
/******/ 		__webpack_require__.hmrD = currentModuleData;
/******/ 		
/******/ 		__webpack_require__.i.push(function (options) {
/******/ 			var module = options.module;
/******/ 			var require = createRequire(options.require, options.id);
/******/ 			module.hot = createModuleHotObject(options.id, module);
/******/ 			module.parents = currentParents;
/******/ 			module.children = [];
/******/ 			currentParents = [];
/******/ 			options.require = require;
/******/ 		});
/******/ 		
/******/ 		__webpack_require__.hmrC = {};
/******/ 		__webpack_require__.hmrI = {};
/******/ 		
/******/ 		function createRequire(require, moduleId) {
/******/ 			var me = installedModules[moduleId];
/******/ 			if (!me) return require;
/******/ 			var fn = function (request) {
/******/ 				if (me.hot.active) {
/******/ 					if (installedModules[request]) {
/******/ 						var parents = installedModules[request].parents;
/******/ 						if (parents.indexOf(moduleId) === -1) {
/******/ 							parents.push(moduleId);
/******/ 						}
/******/ 					} else {
/******/ 						currentParents = [moduleId];
/******/ 						currentChildModule = request;
/******/ 					}
/******/ 					if (me.children.indexOf(request) === -1) {
/******/ 						me.children.push(request);
/******/ 					}
/******/ 				} else {
/******/ 					console.warn(
/******/ 						"[HMR] unexpected require(" +
/******/ 							request +
/******/ 							") from disposed module " +
/******/ 							moduleId
/******/ 					);
/******/ 					currentParents = [];
/******/ 				}
/******/ 				return require(request);
/******/ 			};
/******/ 			var createPropertyDescriptor = function (name) {
/******/ 				return {
/******/ 					configurable: true,
/******/ 					enumerable: true,
/******/ 					get: function () {
/******/ 						return require[name];
/******/ 					},
/******/ 					set: function (value) {
/******/ 						require[name] = value;
/******/ 					}
/******/ 				};
/******/ 			};
/******/ 			for (var name in require) {
/******/ 				if (Object.prototype.hasOwnProperty.call(require, name) && name !== "e") {
/******/ 					Object.defineProperty(fn, name, createPropertyDescriptor(name));
/******/ 				}
/******/ 			}
/******/ 			fn.e = function (chunkId) {
/******/ 				return trackBlockingPromise(require.e(chunkId));
/******/ 			};
/******/ 			return fn;
/******/ 		}
/******/ 		
/******/ 		function createModuleHotObject(moduleId, me) {
/******/ 			var _main = currentChildModule !== moduleId;
/******/ 			var hot = {
/******/ 				// private stuff
/******/ 				_acceptedDependencies: {},
/******/ 				_acceptedErrorHandlers: {},
/******/ 				_declinedDependencies: {},
/******/ 				_selfAccepted: false,
/******/ 				_selfDeclined: false,
/******/ 				_selfInvalidated: false,
/******/ 				_disposeHandlers: [],
/******/ 				_main: _main,
/******/ 				_requireSelf: function () {
/******/ 					currentParents = me.parents.slice();
/******/ 					currentChildModule = _main ? undefined : moduleId;
/******/ 					__webpack_require__(moduleId);
/******/ 				},
/******/ 		
/******/ 				// Module API
/******/ 				active: true,
/******/ 				accept: function (dep, callback, errorHandler) {
/******/ 					if (dep === undefined) hot._selfAccepted = true;
/******/ 					else if (typeof dep === "function") hot._selfAccepted = dep;
/******/ 					else if (typeof dep === "object" && dep !== null) {
/******/ 						for (var i = 0; i < dep.length; i++) {
/******/ 							hot._acceptedDependencies[dep[i]] = callback || function () {};
/******/ 							hot._acceptedErrorHandlers[dep[i]] = errorHandler;
/******/ 						}
/******/ 					} else {
/******/ 						hot._acceptedDependencies[dep] = callback || function () {};
/******/ 						hot._acceptedErrorHandlers[dep] = errorHandler;
/******/ 					}
/******/ 				},
/******/ 				decline: function (dep) {
/******/ 					if (dep === undefined) hot._selfDeclined = true;
/******/ 					else if (typeof dep === "object" && dep !== null)
/******/ 						for (var i = 0; i < dep.length; i++)
/******/ 							hot._declinedDependencies[dep[i]] = true;
/******/ 					else hot._declinedDependencies[dep] = true;
/******/ 				},
/******/ 				dispose: function (callback) {
/******/ 					hot._disposeHandlers.push(callback);
/******/ 				},
/******/ 				addDisposeHandler: function (callback) {
/******/ 					hot._disposeHandlers.push(callback);
/******/ 				},
/******/ 				removeDisposeHandler: function (callback) {
/******/ 					var idx = hot._disposeHandlers.indexOf(callback);
/******/ 					if (idx >= 0) hot._disposeHandlers.splice(idx, 1);
/******/ 				},
/******/ 				invalidate: function () {
/******/ 					this._selfInvalidated = true;
/******/ 					switch (currentStatus) {
/******/ 						case "idle":
/******/ 							currentUpdateApplyHandlers = [];
/******/ 							Object.keys(__webpack_require__.hmrI).forEach(function (key) {
/******/ 								__webpack_require__.hmrI[key](
/******/ 									moduleId,
/******/ 									currentUpdateApplyHandlers
/******/ 								);
/******/ 							});
/******/ 							setStatus("ready");
/******/ 							break;
/******/ 						case "ready":
/******/ 							Object.keys(__webpack_require__.hmrI).forEach(function (key) {
/******/ 								__webpack_require__.hmrI[key](
/******/ 									moduleId,
/******/ 									currentUpdateApplyHandlers
/******/ 								);
/******/ 							});
/******/ 							break;
/******/ 						case "prepare":
/******/ 						case "check":
/******/ 						case "dispose":
/******/ 						case "apply":
/******/ 							(queuedInvalidatedModules = queuedInvalidatedModules || []).push(
/******/ 								moduleId
/******/ 							);
/******/ 							break;
/******/ 						default:
/******/ 							// ignore requests in error states
/******/ 							break;
/******/ 					}
/******/ 				},
/******/ 		
/******/ 				// Management API
/******/ 				check: hotCheck,
/******/ 				apply: hotApply,
/******/ 				status: function (l) {
/******/ 					if (!l) return currentStatus;
/******/ 					registeredStatusHandlers.push(l);
/******/ 				},
/******/ 				addStatusHandler: function (l) {
/******/ 					registeredStatusHandlers.push(l);
/******/ 				},
/******/ 				removeStatusHandler: function (l) {
/******/ 					var idx = registeredStatusHandlers.indexOf(l);
/******/ 					if (idx >= 0) registeredStatusHandlers.splice(idx, 1);
/******/ 				},
/******/ 		
/******/ 				//inherit from previous dispose call
/******/ 				data: currentModuleData[moduleId]
/******/ 			};
/******/ 			currentChildModule = undefined;
/******/ 			return hot;
/******/ 		}
/******/ 		
/******/ 		function setStatus(newStatus) {
/******/ 			currentStatus = newStatus;
/******/ 			var results = [];
/******/ 		
/******/ 			for (var i = 0; i < registeredStatusHandlers.length; i++)
/******/ 				results[i] = registeredStatusHandlers[i].call(null, newStatus);
/******/ 		
/******/ 			return Promise.all(results);
/******/ 		}
/******/ 		
/******/ 		function unblock() {
/******/ 			if (--blockingPromises === 0) {
/******/ 				setStatus("ready").then(function () {
/******/ 					if (blockingPromises === 0) {
/******/ 						var list = blockingPromisesWaiting;
/******/ 						blockingPromisesWaiting = [];
/******/ 						for (var i = 0; i < list.length; i++) {
/******/ 							list[i]();
/******/ 						}
/******/ 					}
/******/ 				});
/******/ 			}
/******/ 		}
/******/ 		
/******/ 		function trackBlockingPromise(promise) {
/******/ 			switch (currentStatus) {
/******/ 				case "ready":
/******/ 					setStatus("prepare");
/******/ 				/* fallthrough */
/******/ 				case "prepare":
/******/ 					blockingPromises++;
/******/ 					promise.then(unblock, unblock);
/******/ 					return promise;
/******/ 				default:
/******/ 					return promise;
/******/ 			}
/******/ 		}
/******/ 		
/******/ 		function waitForBlockingPromises(fn) {
/******/ 			if (blockingPromises === 0) return fn();
/******/ 			return new Promise(function (resolve) {
/******/ 				blockingPromisesWaiting.push(function () {
/******/ 					resolve(fn());
/******/ 				});
/******/ 			});
/******/ 		}
/******/ 		
/******/ 		function hotCheck(applyOnUpdate) {
/******/ 			if (currentStatus !== "idle") {
/******/ 				throw new Error("check() is only allowed in idle status");
/******/ 			}
/******/ 			return setStatus("check")
/******/ 				.then(__webpack_require__.hmrM)
/******/ 				.then(function (update) {
/******/ 					if (!update) {
/******/ 						return setStatus(applyInvalidatedModules() ? "ready" : "idle").then(
/******/ 							function () {
/******/ 								return null;
/******/ 							}
/******/ 						);
/******/ 					}
/******/ 		
/******/ 					return setStatus("prepare").then(function () {
/******/ 						var updatedModules = [];
/******/ 						currentUpdateApplyHandlers = [];
/******/ 		
/******/ 						return Promise.all(
/******/ 							Object.keys(__webpack_require__.hmrC).reduce(function (
/******/ 								promises,
/******/ 								key
/******/ 							) {
/******/ 								__webpack_require__.hmrC[key](
/******/ 									update.c,
/******/ 									update.r,
/******/ 									update.m,
/******/ 									promises,
/******/ 									currentUpdateApplyHandlers,
/******/ 									updatedModules
/******/ 								);
/******/ 								return promises;
/******/ 							},
/******/ 							[])
/******/ 						).then(function () {
/******/ 							return waitForBlockingPromises(function () {
/******/ 								if (applyOnUpdate) {
/******/ 									return internalApply(applyOnUpdate);
/******/ 								} else {
/******/ 									return setStatus("ready").then(function () {
/******/ 										return updatedModules;
/******/ 									});
/******/ 								}
/******/ 							});
/******/ 						});
/******/ 					});
/******/ 				});
/******/ 		}
/******/ 		
/******/ 		function hotApply(options) {
/******/ 			if (currentStatus !== "ready") {
/******/ 				return Promise.resolve().then(function () {
/******/ 					throw new Error(
/******/ 						"apply() is only allowed in ready status (state: " +
/******/ 							currentStatus +
/******/ 							")"
/******/ 					);
/******/ 				});
/******/ 			}
/******/ 			return internalApply(options);
/******/ 		}
/******/ 		
/******/ 		function internalApply(options) {
/******/ 			options = options || {};
/******/ 		
/******/ 			applyInvalidatedModules();
/******/ 		
/******/ 			var results = currentUpdateApplyHandlers.map(function (handler) {
/******/ 				return handler(options);
/******/ 			});
/******/ 			currentUpdateApplyHandlers = undefined;
/******/ 		
/******/ 			var errors = results
/******/ 				.map(function (r) {
/******/ 					return r.error;
/******/ 				})
/******/ 				.filter(Boolean);
/******/ 		
/******/ 			if (errors.length > 0) {
/******/ 				return setStatus("abort").then(function () {
/******/ 					throw errors[0];
/******/ 				});
/******/ 			}
/******/ 		
/******/ 			// Now in "dispose" phase
/******/ 			var disposePromise = setStatus("dispose");
/******/ 		
/******/ 			results.forEach(function (result) {
/******/ 				if (result.dispose) result.dispose();
/******/ 			});
/******/ 		
/******/ 			// Now in "apply" phase
/******/ 			var applyPromise = setStatus("apply");
/******/ 		
/******/ 			var error;
/******/ 			var reportError = function (err) {
/******/ 				if (!error) error = err;
/******/ 			};
/******/ 		
/******/ 			var outdatedModules = [];
/******/ 			results.forEach(function (result) {
/******/ 				if (result.apply) {
/******/ 					var modules = result.apply(reportError);
/******/ 					if (modules) {
/******/ 						for (var i = 0; i < modules.length; i++) {
/******/ 							outdatedModules.push(modules[i]);
/******/ 						}
/******/ 					}
/******/ 				}
/******/ 			});
/******/ 		
/******/ 			return Promise.all([disposePromise, applyPromise]).then(function () {
/******/ 				// handle errors in accept handlers and self accepted module load
/******/ 				if (error) {
/******/ 					return setStatus("fail").then(function () {
/******/ 						throw error;
/******/ 					});
/******/ 				}
/******/ 		
/******/ 				if (queuedInvalidatedModules) {
/******/ 					return internalApply(options).then(function (list) {
/******/ 						outdatedModules.forEach(function (moduleId) {
/******/ 							if (list.indexOf(moduleId) < 0) list.push(moduleId);
/******/ 						});
/******/ 						return list;
/******/ 					});
/******/ 				}
/******/ 		
/******/ 				return setStatus("idle").then(function () {
/******/ 					return outdatedModules;
/******/ 				});
/******/ 			});
/******/ 		}
/******/ 		
/******/ 		function applyInvalidatedModules() {
/******/ 			if (queuedInvalidatedModules) {
/******/ 				if (!currentUpdateApplyHandlers) currentUpdateApplyHandlers = [];
/******/ 				Object.keys(__webpack_require__.hmrI).forEach(function (key) {
/******/ 					queuedInvalidatedModules.forEach(function (moduleId) {
/******/ 						__webpack_require__.hmrI[key](
/******/ 							moduleId,
/******/ 							currentUpdateApplyHandlers
/******/ 						);
/******/ 					});
/******/ 				});
/******/ 				queuedInvalidatedModules = undefined;
/******/ 				return true;
/******/ 			}
/******/ 		}
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/publicPath */
/******/ 	(() => {
/******/ 		var scriptUrl;
/******/ 		if (__webpack_require__.g.importScripts) scriptUrl = __webpack_require__.g.location + "";
/******/ 		var document = __webpack_require__.g.document;
/******/ 		if (!scriptUrl && document) {
/******/ 			if (document.currentScript)
/******/ 				scriptUrl = document.currentScript.src;
/******/ 			if (!scriptUrl) {
/******/ 				var scripts = document.getElementsByTagName("script");
/******/ 				if(scripts.length) {
/******/ 					var i = scripts.length - 1;
/******/ 					while (i > -1 && !scriptUrl) scriptUrl = scripts[i--].src;
/******/ 				}
/******/ 			}
/******/ 		}
/******/ 		// When supporting browsers where an automatic publicPath is not supported you must specify an output.publicPath manually via configuration
/******/ 		// or pass an empty string ("") and set the __webpack_public_path__ variable from your code to use your own logic.
/******/ 		if (!scriptUrl) throw new Error("Automatic publicPath is not supported in this browser");
/******/ 		scriptUrl = scriptUrl.replace(/#.*$/, "").replace(/\?.*$/, "").replace(/\/[^\/]+$/, "/");
/******/ 		__webpack_require__.p = scriptUrl + "../../";
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/css loading */
/******/ 	(() => {
/******/ 		if (typeof document === "undefined") return;
/******/ 		var createStylesheet = (chunkId, fullhref, oldTag, resolve, reject) => {
/******/ 			var linkTag = document.createElement("link");
/******/ 		
/******/ 			linkTag.rel = "stylesheet";
/******/ 			linkTag.type = "text/css";
/******/ 			var onLinkComplete = (event) => {
/******/ 				// avoid mem leaks.
/******/ 				linkTag.onerror = linkTag.onload = null;
/******/ 				if (event.type === 'load') {
/******/ 					resolve();
/******/ 				} else {
/******/ 					var errorType = event && (event.type === 'load' ? 'missing' : event.type);
/******/ 					var realHref = event && event.target && event.target.href || fullhref;
/******/ 					var err = new Error("Loading CSS chunk " + chunkId + " failed.\n(" + realHref + ")");
/******/ 					err.code = "CSS_CHUNK_LOAD_FAILED";
/******/ 					err.type = errorType;
/******/ 					err.request = realHref;
/******/ 					if (linkTag.parentNode) linkTag.parentNode.removeChild(linkTag)
/******/ 					reject(err);
/******/ 				}
/******/ 			}
/******/ 			linkTag.onerror = linkTag.onload = onLinkComplete;
/******/ 			linkTag.href = fullhref;
/******/ 		
/******/ 			if (oldTag) {
/******/ 				oldTag.parentNode.insertBefore(linkTag, oldTag.nextSibling);
/******/ 			} else {
/******/ 				document.head.appendChild(linkTag);
/******/ 			}
/******/ 			return linkTag;
/******/ 		};
/******/ 		var findStylesheet = (href, fullhref) => {
/******/ 			var existingLinkTags = document.getElementsByTagName("link");
/******/ 			for(var i = 0; i < existingLinkTags.length; i++) {
/******/ 				var tag = existingLinkTags[i];
/******/ 				var dataHref = tag.getAttribute("data-href") || tag.getAttribute("href");
/******/ 				if(tag.rel === "stylesheet" && (dataHref === href || dataHref === fullhref)) return tag;
/******/ 			}
/******/ 			var existingStyleTags = document.getElementsByTagName("style");
/******/ 			for(var i = 0; i < existingStyleTags.length; i++) {
/******/ 				var tag = existingStyleTags[i];
/******/ 				var dataHref = tag.getAttribute("data-href");
/******/ 				if(dataHref === href || dataHref === fullhref) return tag;
/******/ 			}
/******/ 		};
/******/ 		var loadStylesheet = (chunkId) => {
/******/ 			return new Promise((resolve, reject) => {
/******/ 				var href = __webpack_require__.miniCssF(chunkId);
/******/ 				var fullhref = __webpack_require__.p + href;
/******/ 				if(findStylesheet(href, fullhref)) return resolve();
/******/ 				createStylesheet(chunkId, fullhref, null, resolve, reject);
/******/ 			});
/******/ 		}
/******/ 		// no chunk loading
/******/ 		
/******/ 		var oldTags = [];
/******/ 		var newTags = [];
/******/ 		var applyHandler = (options) => {
/******/ 			return { dispose: () => {
/******/ 				for(var i = 0; i < oldTags.length; i++) {
/******/ 					var oldTag = oldTags[i];
/******/ 					if(oldTag.parentNode) oldTag.parentNode.removeChild(oldTag);
/******/ 				}
/******/ 				oldTags.length = 0;
/******/ 			}, apply: () => {
/******/ 				for(var i = 0; i < newTags.length; i++) newTags[i].rel = "stylesheet";
/******/ 				newTags.length = 0;
/******/ 			} };
/******/ 		}
/******/ 		__webpack_require__.hmrC.miniCss = (chunkIds, removedChunks, removedModules, promises, applyHandlers, updatedModulesList) => {
/******/ 			applyHandlers.push(applyHandler);
/******/ 			chunkIds.forEach((chunkId) => {
/******/ 				var href = __webpack_require__.miniCssF(chunkId);
/******/ 				var fullhref = __webpack_require__.p + href;
/******/ 				var oldTag = findStylesheet(href, fullhref);
/******/ 				if(!oldTag) return;
/******/ 				promises.push(new Promise((resolve, reject) => {
/******/ 					var tag = createStylesheet(chunkId, fullhref, oldTag, () => {
/******/ 						tag.as = "style";
/******/ 						tag.rel = "preload";
/******/ 						resolve();
/******/ 					}, reject);
/******/ 					oldTags.push(oldTag);
/******/ 					newTags.push(tag);
/******/ 				}));
/******/ 			});
/******/ 		}
/******/ 	})();
/******/ 	
/******/ 	/* webpack/runtime/jsonp chunk loading */
/******/ 	(() => {
/******/ 		// no baseURI
/******/ 		
/******/ 		// object to store loaded and loading chunks
/******/ 		// undefined = chunk not loaded, null = chunk preloaded/prefetched
/******/ 		// [resolve, reject, Promise] = chunk loading, 0 = chunk loaded
/******/ 		var installedChunks = __webpack_require__.hmrS_jsonp = __webpack_require__.hmrS_jsonp || {
/******/ 			"main": 0
/******/ 		};
/******/ 		
/******/ 		// no chunk on demand loading
/******/ 		
/******/ 		// no prefetching
/******/ 		
/******/ 		// no preloaded
/******/ 		
/******/ 		var currentUpdatedModulesList;
/******/ 		var waitingUpdateResolves = {};
/******/ 		function loadUpdateChunk(chunkId, updatedModulesList) {
/******/ 			currentUpdatedModulesList = updatedModulesList;
/******/ 			return new Promise((resolve, reject) => {
/******/ 				waitingUpdateResolves[chunkId] = resolve;
/******/ 				// start update chunk loading
/******/ 				var url = __webpack_require__.p + __webpack_require__.hu(chunkId);
/******/ 				// create error before stack unwound to get useful stacktrace later
/******/ 				var error = new Error();
/******/ 				var loadingEnded = (event) => {
/******/ 					if(waitingUpdateResolves[chunkId]) {
/******/ 						waitingUpdateResolves[chunkId] = undefined
/******/ 						var errorType = event && (event.type === 'load' ? 'missing' : event.type);
/******/ 						var realSrc = event && event.target && event.target.src;
/******/ 						error.message = 'Loading hot update chunk ' + chunkId + ' failed.\n(' + errorType + ': ' + realSrc + ')';
/******/ 						error.name = 'ChunkLoadError';
/******/ 						error.type = errorType;
/******/ 						error.request = realSrc;
/******/ 						reject(error);
/******/ 					}
/******/ 				};
/******/ 				__webpack_require__.l(url, loadingEnded);
/******/ 			});
/******/ 		}
/******/ 		
/******/ 		self["webpackHotUpdatewebpack_demo"] = (chunkId, moreModules, runtime) => {
/******/ 			for(var moduleId in moreModules) {
/******/ 				if(__webpack_require__.o(moreModules, moduleId)) {
/******/ 					currentUpdate[moduleId] = moreModules[moduleId];
/******/ 					if(currentUpdatedModulesList) currentUpdatedModulesList.push(moduleId);
/******/ 				}
/******/ 			}
/******/ 			if(runtime) currentUpdateRuntime.push(runtime);
/******/ 			if(waitingUpdateResolves[chunkId]) {
/******/ 				waitingUpdateResolves[chunkId]();
/******/ 				waitingUpdateResolves[chunkId] = undefined;
/******/ 			}
/******/ 		};
/******/ 		
/******/ 		var currentUpdateChunks;
/******/ 		var currentUpdate;
/******/ 		var currentUpdateRemovedChunks;
/******/ 		var currentUpdateRuntime;
/******/ 		function applyHandler(options) {
/******/ 			if (__webpack_require__.f) delete __webpack_require__.f.jsonpHmr;
/******/ 			currentUpdateChunks = undefined;
/******/ 			function getAffectedModuleEffects(updateModuleId) {
/******/ 				var outdatedModules = [updateModuleId];
/******/ 				var outdatedDependencies = {};
/******/ 		
/******/ 				var queue = outdatedModules.map(function (id) {
/******/ 					return {
/******/ 						chain: [id],
/******/ 						id: id
/******/ 					};
/******/ 				});
/******/ 				while (queue.length > 0) {
/******/ 					var queueItem = queue.pop();
/******/ 					var moduleId = queueItem.id;
/******/ 					var chain = queueItem.chain;
/******/ 					var module = __webpack_require__.c[moduleId];
/******/ 					if (
/******/ 						!module ||
/******/ 						(module.hot._selfAccepted && !module.hot._selfInvalidated)
/******/ 					)
/******/ 						continue;
/******/ 					if (module.hot._selfDeclined) {
/******/ 						return {
/******/ 							type: "self-declined",
/******/ 							chain: chain,
/******/ 							moduleId: moduleId
/******/ 						};
/******/ 					}
/******/ 					if (module.hot._main) {
/******/ 						return {
/******/ 							type: "unaccepted",
/******/ 							chain: chain,
/******/ 							moduleId: moduleId
/******/ 						};
/******/ 					}
/******/ 					for (var i = 0; i < module.parents.length; i++) {
/******/ 						var parentId = module.parents[i];
/******/ 						var parent = __webpack_require__.c[parentId];
/******/ 						if (!parent) continue;
/******/ 						if (parent.hot._declinedDependencies[moduleId]) {
/******/ 							return {
/******/ 								type: "declined",
/******/ 								chain: chain.concat([parentId]),
/******/ 								moduleId: moduleId,
/******/ 								parentId: parentId
/******/ 							};
/******/ 						}
/******/ 						if (outdatedModules.indexOf(parentId) !== -1) continue;
/******/ 						if (parent.hot._acceptedDependencies[moduleId]) {
/******/ 							if (!outdatedDependencies[parentId])
/******/ 								outdatedDependencies[parentId] = [];
/******/ 							addAllToSet(outdatedDependencies[parentId], [moduleId]);
/******/ 							continue;
/******/ 						}
/******/ 						delete outdatedDependencies[parentId];
/******/ 						outdatedModules.push(parentId);
/******/ 						queue.push({
/******/ 							chain: chain.concat([parentId]),
/******/ 							id: parentId
/******/ 						});
/******/ 					}
/******/ 				}
/******/ 		
/******/ 				return {
/******/ 					type: "accepted",
/******/ 					moduleId: updateModuleId,
/******/ 					outdatedModules: outdatedModules,
/******/ 					outdatedDependencies: outdatedDependencies
/******/ 				};
/******/ 			}
/******/ 		
/******/ 			function addAllToSet(a, b) {
/******/ 				for (var i = 0; i < b.length; i++) {
/******/ 					var item = b[i];
/******/ 					if (a.indexOf(item) === -1) a.push(item);
/******/ 				}
/******/ 			}
/******/ 		
/******/ 			// at begin all updates modules are outdated
/******/ 			// the "outdated" status can propagate to parents if they don't accept the children
/******/ 			var outdatedDependencies = {};
/******/ 			var outdatedModules = [];
/******/ 			var appliedUpdate = {};
/******/ 		
/******/ 			var warnUnexpectedRequire = function warnUnexpectedRequire(module) {
/******/ 				console.warn(
/******/ 					"[HMR] unexpected require(" + module.id + ") to disposed module"
/******/ 				);
/******/ 			};
/******/ 		
/******/ 			for (var moduleId in currentUpdate) {
/******/ 				if (__webpack_require__.o(currentUpdate, moduleId)) {
/******/ 					var newModuleFactory = currentUpdate[moduleId];
/******/ 					/** @type {TODO} */
/******/ 					var result;
/******/ 					if (newModuleFactory) {
/******/ 						result = getAffectedModuleEffects(moduleId);
/******/ 					} else {
/******/ 						result = {
/******/ 							type: "disposed",
/******/ 							moduleId: moduleId
/******/ 						};
/******/ 					}
/******/ 					/** @type {Error|false} */
/******/ 					var abortError = false;
/******/ 					var doApply = false;
/******/ 					var doDispose = false;
/******/ 					var chainInfo = "";
/******/ 					if (result.chain) {
/******/ 						chainInfo = "\nUpdate propagation: " + result.chain.join(" -> ");
/******/ 					}
/******/ 					switch (result.type) {
/******/ 						case "self-declined":
/******/ 							if (options.onDeclined) options.onDeclined(result);
/******/ 							if (!options.ignoreDeclined)
/******/ 								abortError = new Error(
/******/ 									"Aborted because of self decline: " +
/******/ 										result.moduleId +
/******/ 										chainInfo
/******/ 								);
/******/ 							break;
/******/ 						case "declined":
/******/ 							if (options.onDeclined) options.onDeclined(result);
/******/ 							if (!options.ignoreDeclined)
/******/ 								abortError = new Error(
/******/ 									"Aborted because of declined dependency: " +
/******/ 										result.moduleId +
/******/ 										" in " +
/******/ 										result.parentId +
/******/ 										chainInfo
/******/ 								);
/******/ 							break;
/******/ 						case "unaccepted":
/******/ 							if (options.onUnaccepted) options.onUnaccepted(result);
/******/ 							if (!options.ignoreUnaccepted)
/******/ 								abortError = new Error(
/******/ 									"Aborted because " + moduleId + " is not accepted" + chainInfo
/******/ 								);
/******/ 							break;
/******/ 						case "accepted":
/******/ 							if (options.onAccepted) options.onAccepted(result);
/******/ 							doApply = true;
/******/ 							break;
/******/ 						case "disposed":
/******/ 							if (options.onDisposed) options.onDisposed(result);
/******/ 							doDispose = true;
/******/ 							break;
/******/ 						default:
/******/ 							throw new Error("Unexception type " + result.type);
/******/ 					}
/******/ 					if (abortError) {
/******/ 						return {
/******/ 							error: abortError
/******/ 						};
/******/ 					}
/******/ 					if (doApply) {
/******/ 						appliedUpdate[moduleId] = newModuleFactory;
/******/ 						addAllToSet(outdatedModules, result.outdatedModules);
/******/ 						for (moduleId in result.outdatedDependencies) {
/******/ 							if (__webpack_require__.o(result.outdatedDependencies, moduleId)) {
/******/ 								if (!outdatedDependencies[moduleId])
/******/ 									outdatedDependencies[moduleId] = [];
/******/ 								addAllToSet(
/******/ 									outdatedDependencies[moduleId],
/******/ 									result.outdatedDependencies[moduleId]
/******/ 								);
/******/ 							}
/******/ 						}
/******/ 					}
/******/ 					if (doDispose) {
/******/ 						addAllToSet(outdatedModules, [result.moduleId]);
/******/ 						appliedUpdate[moduleId] = warnUnexpectedRequire;
/******/ 					}
/******/ 				}
/******/ 			}
/******/ 			currentUpdate = undefined;
/******/ 		
/******/ 			// Store self accepted outdated modules to require them later by the module system
/******/ 			var outdatedSelfAcceptedModules = [];
/******/ 			for (var j = 0; j < outdatedModules.length; j++) {
/******/ 				var outdatedModuleId = outdatedModules[j];
/******/ 				var module = __webpack_require__.c[outdatedModuleId];
/******/ 				if (
/******/ 					module &&
/******/ 					(module.hot._selfAccepted || module.hot._main) &&
/******/ 					// removed self-accepted modules should not be required
/******/ 					appliedUpdate[outdatedModuleId] !== warnUnexpectedRequire &&
/******/ 					// when called invalidate self-accepting is not possible
/******/ 					!module.hot._selfInvalidated
/******/ 				) {
/******/ 					outdatedSelfAcceptedModules.push({
/******/ 						module: outdatedModuleId,
/******/ 						require: module.hot._requireSelf,
/******/ 						errorHandler: module.hot._selfAccepted
/******/ 					});
/******/ 				}
/******/ 			}
/******/ 		
/******/ 			var moduleOutdatedDependencies;
/******/ 		
/******/ 			return {
/******/ 				dispose: function () {
/******/ 					currentUpdateRemovedChunks.forEach(function (chunkId) {
/******/ 						delete installedChunks[chunkId];
/******/ 					});
/******/ 					currentUpdateRemovedChunks = undefined;
/******/ 		
/******/ 					var idx;
/******/ 					var queue = outdatedModules.slice();
/******/ 					while (queue.length > 0) {
/******/ 						var moduleId = queue.pop();
/******/ 						var module = __webpack_require__.c[moduleId];
/******/ 						if (!module) continue;
/******/ 		
/******/ 						var data = {};
/******/ 		
/******/ 						// Call dispose handlers
/******/ 						var disposeHandlers = module.hot._disposeHandlers;
/******/ 						for (j = 0; j < disposeHandlers.length; j++) {
/******/ 							disposeHandlers[j].call(null, data);
/******/ 						}
/******/ 						__webpack_require__.hmrD[moduleId] = data;
/******/ 		
/******/ 						// disable module (this disables requires from this module)
/******/ 						module.hot.active = false;
/******/ 		
/******/ 						// remove module from cache
/******/ 						delete __webpack_require__.c[moduleId];
/******/ 		
/******/ 						// when disposing there is no need to call dispose handler
/******/ 						delete outdatedDependencies[moduleId];
/******/ 		
/******/ 						// remove "parents" references from all children
/******/ 						for (j = 0; j < module.children.length; j++) {
/******/ 							var child = __webpack_require__.c[module.children[j]];
/******/ 							if (!child) continue;
/******/ 							idx = child.parents.indexOf(moduleId);
/******/ 							if (idx >= 0) {
/******/ 								child.parents.splice(idx, 1);
/******/ 							}
/******/ 						}
/******/ 					}
/******/ 		
/******/ 					// remove outdated dependency from module children
/******/ 					var dependency;
/******/ 					for (var outdatedModuleId in outdatedDependencies) {
/******/ 						if (__webpack_require__.o(outdatedDependencies, outdatedModuleId)) {
/******/ 							module = __webpack_require__.c[outdatedModuleId];
/******/ 							if (module) {
/******/ 								moduleOutdatedDependencies =
/******/ 									outdatedDependencies[outdatedModuleId];
/******/ 								for (j = 0; j < moduleOutdatedDependencies.length; j++) {
/******/ 									dependency = moduleOutdatedDependencies[j];
/******/ 									idx = module.children.indexOf(dependency);
/******/ 									if (idx >= 0) module.children.splice(idx, 1);
/******/ 								}
/******/ 							}
/******/ 						}
/******/ 					}
/******/ 				},
/******/ 				apply: function (reportError) {
/******/ 					// insert new code
/******/ 					for (var updateModuleId in appliedUpdate) {
/******/ 						if (__webpack_require__.o(appliedUpdate, updateModuleId)) {
/******/ 							__webpack_require__.m[updateModuleId] = appliedUpdate[updateModuleId];
/******/ 						}
/******/ 					}
/******/ 		
/******/ 					// run new runtime modules
/******/ 					for (var i = 0; i < currentUpdateRuntime.length; i++) {
/******/ 						currentUpdateRuntime[i](__webpack_require__);
/******/ 					}
/******/ 		
/******/ 					// call accept handlers
/******/ 					for (var outdatedModuleId in outdatedDependencies) {
/******/ 						if (__webpack_require__.o(outdatedDependencies, outdatedModuleId)) {
/******/ 							var module = __webpack_require__.c[outdatedModuleId];
/******/ 							if (module) {
/******/ 								moduleOutdatedDependencies =
/******/ 									outdatedDependencies[outdatedModuleId];
/******/ 								var callbacks = [];
/******/ 								var errorHandlers = [];
/******/ 								var dependenciesForCallbacks = [];
/******/ 								for (var j = 0; j < moduleOutdatedDependencies.length; j++) {
/******/ 									var dependency = moduleOutdatedDependencies[j];
/******/ 									var acceptCallback =
/******/ 										module.hot._acceptedDependencies[dependency];
/******/ 									var errorHandler =
/******/ 										module.hot._acceptedErrorHandlers[dependency];
/******/ 									if (acceptCallback) {
/******/ 										if (callbacks.indexOf(acceptCallback) !== -1) continue;
/******/ 										callbacks.push(acceptCallback);
/******/ 										errorHandlers.push(errorHandler);
/******/ 										dependenciesForCallbacks.push(dependency);
/******/ 									}
/******/ 								}
/******/ 								for (var k = 0; k < callbacks.length; k++) {
/******/ 									try {
/******/ 										callbacks[k].call(null, moduleOutdatedDependencies);
/******/ 									} catch (err) {
/******/ 										if (typeof errorHandlers[k] === "function") {
/******/ 											try {
/******/ 												errorHandlers[k](err, {
/******/ 													moduleId: outdatedModuleId,
/******/ 													dependencyId: dependenciesForCallbacks[k]
/******/ 												});
/******/ 											} catch (err2) {
/******/ 												if (options.onErrored) {
/******/ 													options.onErrored({
/******/ 														type: "accept-error-handler-errored",
/******/ 														moduleId: outdatedModuleId,
/******/ 														dependencyId: dependenciesForCallbacks[k],
/******/ 														error: err2,
/******/ 														originalError: err
/******/ 													});
/******/ 												}
/******/ 												if (!options.ignoreErrored) {
/******/ 													reportError(err2);
/******/ 													reportError(err);
/******/ 												}
/******/ 											}
/******/ 										} else {
/******/ 											if (options.onErrored) {
/******/ 												options.onErrored({
/******/ 													type: "accept-errored",
/******/ 													moduleId: outdatedModuleId,
/******/ 													dependencyId: dependenciesForCallbacks[k],
/******/ 													error: err
/******/ 												});
/******/ 											}
/******/ 											if (!options.ignoreErrored) {
/******/ 												reportError(err);
/******/ 											}
/******/ 										}
/******/ 									}
/******/ 								}
/******/ 							}
/******/ 						}
/******/ 					}
/******/ 		
/******/ 					// Load self accepted modules
/******/ 					for (var o = 0; o < outdatedSelfAcceptedModules.length; o++) {
/******/ 						var item = outdatedSelfAcceptedModules[o];
/******/ 						var moduleId = item.module;
/******/ 						try {
/******/ 							item.require(moduleId);
/******/ 						} catch (err) {
/******/ 							if (typeof item.errorHandler === "function") {
/******/ 								try {
/******/ 									item.errorHandler(err, {
/******/ 										moduleId: moduleId,
/******/ 										module: __webpack_require__.c[moduleId]
/******/ 									});
/******/ 								} catch (err2) {
/******/ 									if (options.onErrored) {
/******/ 										options.onErrored({
/******/ 											type: "self-accept-error-handler-errored",
/******/ 											moduleId: moduleId,
/******/ 											error: err2,
/******/ 											originalError: err
/******/ 										});
/******/ 									}
/******/ 									if (!options.ignoreErrored) {
/******/ 										reportError(err2);
/******/ 										reportError(err);
/******/ 									}
/******/ 								}
/******/ 							} else {
/******/ 								if (options.onErrored) {
/******/ 									options.onErrored({
/******/ 										type: "self-accept-errored",
/******/ 										moduleId: moduleId,
/******/ 										error: err
/******/ 									});
/******/ 								}
/******/ 								if (!options.ignoreErrored) {
/******/ 									reportError(err);
/******/ 								}
/******/ 							}
/******/ 						}
/******/ 					}
/******/ 		
/******/ 					return outdatedModules;
/******/ 				}
/******/ 			};
/******/ 		}
/******/ 		__webpack_require__.hmrI.jsonp = function (moduleId, applyHandlers) {
/******/ 			if (!currentUpdate) {
/******/ 				currentUpdate = {};
/******/ 				currentUpdateRuntime = [];
/******/ 				currentUpdateRemovedChunks = [];
/******/ 				applyHandlers.push(applyHandler);
/******/ 			}
/******/ 			if (!__webpack_require__.o(currentUpdate, moduleId)) {
/******/ 				currentUpdate[moduleId] = __webpack_require__.m[moduleId];
/******/ 			}
/******/ 		};
/******/ 		__webpack_require__.hmrC.jsonp = function (
/******/ 			chunkIds,
/******/ 			removedChunks,
/******/ 			removedModules,
/******/ 			promises,
/******/ 			applyHandlers,
/******/ 			updatedModulesList
/******/ 		) {
/******/ 			applyHandlers.push(applyHandler);
/******/ 			currentUpdateChunks = {};
/******/ 			currentUpdateRemovedChunks = removedChunks;
/******/ 			currentUpdate = removedModules.reduce(function (obj, key) {
/******/ 				obj[key] = false;
/******/ 				return obj;
/******/ 			}, {});
/******/ 			currentUpdateRuntime = [];
/******/ 			chunkIds.forEach(function (chunkId) {
/******/ 				if (
/******/ 					__webpack_require__.o(installedChunks, chunkId) &&
/******/ 					installedChunks[chunkId] !== undefined
/******/ 				) {
/******/ 					promises.push(loadUpdateChunk(chunkId, updatedModulesList));
/******/ 					currentUpdateChunks[chunkId] = true;
/******/ 				} else {
/******/ 					currentUpdateChunks[chunkId] = false;
/******/ 				}
/******/ 			});
/******/ 			if (__webpack_require__.f) {
/******/ 				__webpack_require__.f.jsonpHmr = function (chunkId, promises) {
/******/ 					if (
/******/ 						currentUpdateChunks &&
/******/ 						__webpack_require__.o(currentUpdateChunks, chunkId) &&
/******/ 						!currentUpdateChunks[chunkId]
/******/ 					) {
/******/ 						promises.push(loadUpdateChunk(chunkId));
/******/ 						currentUpdateChunks[chunkId] = true;
/******/ 					}
/******/ 				};
/******/ 			}
/******/ 		};
/******/ 		
/******/ 		__webpack_require__.hmrM = () => {
/******/ 			if (typeof fetch === "undefined") throw new Error("No browser support: need fetch API");
/******/ 			return fetch(__webpack_require__.p + __webpack_require__.hmrF()).then((response) => {
/******/ 				if(response.status === 404) return; // no update available
/******/ 				if(!response.ok) throw new Error("Failed to fetch update manifest " + response.statusText);
/******/ 				return response.json();
/******/ 			});
/******/ 		};
/******/ 		
/******/ 		// no on chunks loaded
/******/ 		
/******/ 		// no jsonp function
/******/ 	})();
/******/ 	
/************************************************************************/
/******/ 	
/******/ 	// module cache are used so entry inlining is disabled
/******/ 	// startup
/******/ 	// Load entry module and return exports
/******/ 	var __webpack_exports__ = __webpack_require__("./src/index.js");
/******/ 	
/******/ })()
;