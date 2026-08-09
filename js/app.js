"use strict";

const viewTitles = {
  login: "ログイン | ワークwith",
  register: "新規登録 | ワークwith",
  lobby: "ロビー | ワークwith"
};

/**
 * 指定した画面だけを表示します。
 * History APIを使うため、URLは変わってもページは再読み込みされません。
 */
function showView(viewName, addHistory = true) {
  const nextView = document.querySelector(`[data-view="${viewName}"]`);

  if (!nextView) {
    return;
  }

  document.querySelectorAll(".app-view").forEach(function (view) {
    view.hidden = view !== nextView;
  });

  document.title = viewTitles[viewName] || "ワークwith";

  const nextUrl = `#${viewName}`;
  if (addHistory && window.location.hash !== nextUrl) {
    history.pushState({ view: viewName }, "", nextUrl);
  }

  window.scrollTo(0, 0);
}

/** URLから表示対象を決めます。不明なURLの場合はログイン画面を表示します。 */
function getViewFromUrl() {
  const viewName = window.location.hash.replace("#", "");
  return document.querySelector(`[data-view="${viewName}"]`) ? viewName : "login";
}

// ブラウザの「戻る」「進む」でも、リロードせず画面を切り替えます。
window.addEventListener("popstate", function () {
  showView(getViewFromUrl(), false);
});

// data-routeを持つリンクは、別HTMLを読み込まずSPA内の画面を表示します。
document.addEventListener("click", function (event) {
  const routeLink = event.target.closest("[data-route]");

  if (!routeLink) {
    return;
  }

  event.preventDefault();
  showView(routeLink.dataset.route);
});

// 最初にHTMLを開いたときの画面を決定します。
const initialView = getViewFromUrl();
if (!window.location.hash) {
  history.replaceState({ view: initialView }, "", `#${initialView}`);
}
showView(initialView, false);
