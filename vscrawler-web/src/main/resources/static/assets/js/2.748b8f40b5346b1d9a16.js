webpackJsonp([2],{"01ue":function(e,t,i){e.exports=i.p+"assets/img/new_logo.c73f4e5.png"},eerB:function(e,t,i){"use strict";Object.defineProperty(t,"__esModule",{value:!0});var o=[{route:"/dashboard",name:"dashboard",icon:"home"},{name:"表单页",icon:"ios-copy-outline",children:[{route:"/form/base",name:"基础表单",icon:"compose"},{route:"/form/step",name:"分布表单",icon:"printer"},{route:"/form/advance",name:"高级表单",icon:"map"}]},{name:"列表页",icon:"ios-list-outline",children:[{route:"/list/query",name:"查询表格",icon:"ios-photos-outline"},{route:"/list/base",name:"标准列表",icon:"ios-barcode-outline"},{route:"/list/card",name:"卡片列表",icon:"ios-crop"},{route:"/list/search",name:"搜索列表",icon:"ios-ionic-outline"}]},{name:"详情页",icon:"ios-world-outline",children:[{route:"/detail/base",name:"基础详情页",icon:"ios-drag"},{route:"/detail/advance",name:"高级详情页",icon:"ios-grid-view-outline"}]},{name:"结果页",icon:"clipboard",children:[{route:"/result/success",name:"成功",icon:"ios-checkmark-outline"},{route:"/result/fail",name:"失败",icon:"ios-close-outline"}]},{name:"异常页",icon:"ios-information-outline",children:[{route:"/error/e403",name:"403",icon:"ios-help-outline"},{route:"/error/e404",name:"404",icon:"ios-help-outline"},{route:"/error/e500",name:"500",icon:"ios-help-outline"}]}],n={name:"index",data:function(){return{menus:o,search:"",active:"",open:[]}},created:function(){this.active=this.$route.path,this.open=this.getOpen(this.active)},methods:{route:function(e){this.$router.push(e)},getOpen:function(e){var t=[];return o.map(function(i,o){i.children&&i.children.map(function(i){i.route==e&&t.push(o+"-"+o)})}),t}}},r={render:function(){var e=this,t=this,i=t.$createElement,o=t._self._c||i;return o("div",{staticClass:"main",attrs:{id:"index"}},[o("header",{staticClass:"header"},[t._m(0),t._v(" "),o("ul",{staticClass:"infos"},[o("li",{class:{active:"/list"==this.$route.path}},[o("a",{on:{click:function(){return e.$router.push("/list")}}},[t._v("爬虫列表")])]),t._v(" "),o("li",{class:{active:"/test"==this.$route.path}},[o("a",{on:{click:function(){return e.$router.push("/test")}}},[t._v("抓取测试")])]),t._v(" "),o("li",[o("Input",{staticStyle:{width:"200px"},attrs:{icon:"ios-search",placeholder:"查询..."},model:{value:t.search,callback:function(e){t.search=e},expression:"search"}})],1)])]),t._v(" "),o("section",{staticClass:"router"},[o("router-view")],1)])},staticRenderFns:[function(){var e=this.$createElement,t=this._self._c||e;return t("a",{attrs:{href:""}},[t("img",{attrs:{src:i("01ue"),alt:"logo"}})])}]};var a=i("Z0/y")(n,r,!1,function(e){i("hXWT")},"data-v-101ca9ec",null);t.default=a.exports},hXWT:function(e,t){}});