"use strict";

const topTabs = Array.from(document.querySelectorAll(".top-tab"));
const topPanels = Array.from(document.querySelectorAll(".top-tab-panel"));

function activateTopTab(nextTab) {
  topTabs.forEach(function (tab) {
    const active = tab === nextTab;
    tab.classList.toggle("is-active", active);
    tab.setAttribute("aria-selected", String(active));
    tab.tabIndex = active ? 0 : -1;
  });
  topPanels.forEach(function (panel) {
    panel.hidden = panel.id !== nextTab.getAttribute("aria-controls");
  });
}

topTabs.forEach(function (tab, index) {
  tab.addEventListener("click", function () {
    activateTopTab(tab);
  });
  tab.addEventListener("keydown", function (event) {
    if (event.key !== "ArrowLeft" && event.key !== "ArrowRight") return;
    event.preventDefault();
    const direction = event.key === "ArrowRight" ? 1 : -1;
    const nextTab = topTabs[(index + direction + topTabs.length) % topTabs.length];
    activateTopTab(nextTab);
    nextTab.focus();
  });
});

const topAvatars = Array.from(document.querySelectorAll(".top-avatar"));
topAvatars.forEach(function (avatar) {
  avatar.addEventListener("click", function (event) {
    event.stopPropagation();
    const willOpen = !avatar.classList.contains("is-tooltip-open");
    topAvatars.forEach(function (item) {
      item.classList.remove("is-tooltip-open");
      item.setAttribute("aria-expanded", "false");
    });
    avatar.classList.toggle("is-tooltip-open", willOpen);
    avatar.setAttribute("aria-expanded", String(willOpen));
  });
});

document.addEventListener("click", function () {
  topAvatars.forEach(function (avatar) {
    avatar.classList.remove("is-tooltip-open");
    avatar.setAttribute("aria-expanded", "false");
  });
});
