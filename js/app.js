"use strict";

// 認証の根拠はHttpSessionです。この変数は画面表示中だけ使う一時情報です。
let authenticatedUser = null;

function setAuthenticatedUser(user) {
  authenticatedUser = user;
}

function getAuthenticatedUser() {
  return authenticatedUser;
}

function clearAuthenticatedUser() {
  authenticatedUser = null;
}

const viewTitles = {
  top: "ワークwith | オンライン共同作業スペース",
  login: "ログイン | ワークwith",
  register: "新規登録 | ワークwith",
  lobby: "ロビー | ワークwith",
  "create-room": "プライベート部屋を作成 | ワークwith",
  room: "部屋 | ワークwith"
};

/**
 * 指定した画面だけを表示します。
 * History APIを使うため、URLは変わってもページは再読み込みされません。
 */
function showView(viewName, addHistory = true, queryString = "") {
  const nextView = document.querySelector(`[data-view="${viewName}"]`);

  if (!nextView) {
    return;
  }

  document.querySelectorAll(".app-view").forEach(function (view) {
    view.hidden = view !== nextView;
  });

  document.title = viewTitles[viewName] || "ワークwith";

  const nextUrl = `#${viewName}${queryString ? `?${queryString}` : ""}`;
  if (addHistory && window.location.hash !== nextUrl) {
    history.pushState({ view: viewName }, "", nextUrl);
  }

  window.scrollTo(0, 0);
  document.dispatchEvent(
    new CustomEvent("viewchange", {
      detail: { view: viewName, query: queryString }
    })
  );
}

/** URLから表示対象を決めます。不明なURLやハッシュなしの場合はトップ画面を表示します。 */
function getViewFromUrl() {
  const viewName = window.location.hash.replace("#", "").split("?")[0];
  return document.querySelector(`[data-view="${viewName}"]`) ? viewName : "top";
}

/** ハッシュURLの?以降をURLSearchParamsへ渡せる文字列として取得します。 */
function getQueryFromUrl() {
  return window.location.hash.split("?")[1] || "";
}

// ブラウザの「戻る」「進む」でも、リロードせず画面を切り替えます。
window.addEventListener("popstate", function () {
  showView(getViewFromUrl(), false, getQueryFromUrl());
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
showView(initialView, false, getQueryFromUrl());
