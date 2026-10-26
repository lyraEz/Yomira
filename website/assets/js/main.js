/* Yomira website behaviour. No dependencies. */
(function () {
  "use strict";

  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var $ = function (sel, root) { return (root || document).querySelector(sel); };
  var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };
  var store = {
    get: function (k) { try { return localStorage.getItem(k); } catch (e) { return null; } },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (e) { /* private mode */ } }
  };

  /* Language ------------------------------------------------------------------------------- */

  var LANGS = ["en", "pt", "es", "ru"];
  var dict = window.YOMIRA_I18N || {};

  function pickLang() {
    var q = new URLSearchParams(location.search).get("lang");
    if (q && LANGS.indexOf(q) >= 0) return q;
    var saved = store.get("yomira-lang");
    if (saved && LANGS.indexOf(saved) >= 0) return saved;
    return "en";
  }

  function applyLang(lang) {
    var d = dict[lang] || dict.en || {};
    var fallback = dict.en || {};
    $$("[data-i18n]").forEach(function (el) {
      var key = el.getAttribute("data-i18n");
      var v = d[key] != null ? d[key] : fallback[key];
      if (v != null) el.textContent = v;
    });
    $$("[data-i18n-html]").forEach(function (el) {
      var key = el.getAttribute("data-i18n-html");
      var v = d[key] != null ? d[key] : fallback[key];
      if (v != null) el.innerHTML = v;
    });
    document.documentElement.lang = lang === "pt" ? "pt-BR" : lang;
    if (d["meta.title"]) document.title = d["meta.title"];
    var code = $("#lang-code");
    if (code) code.textContent = lang.toUpperCase();
    $$("#lang-menu [data-lang]").forEach(function (b) {
      b.setAttribute("aria-checked", b.getAttribute("data-lang") === lang ? "true" : "false");
    });
    window.dispatchEvent(new Event("yomira:lang"));
  }

  var currentLang = pickLang();
  applyLang(currentLang);

  var langBtn = $("#lang-btn"), langMenu = $("#lang-menu");
  if (langBtn && langMenu) {
    langBtn.addEventListener("click", function (e) {
      e.stopPropagation();
      var open = !langMenu.classList.contains("open");
      langMenu.classList.toggle("open", open);
      langBtn.setAttribute("aria-expanded", open ? "true" : "false");
    });
    $$("[data-lang]", langMenu).forEach(function (b) {
      b.addEventListener("click", function () {
        currentLang = b.getAttribute("data-lang");
        store.set("yomira-lang", currentLang);
        applyLang(currentLang);
        langMenu.classList.remove("open");
        langBtn.setAttribute("aria-expanded", "false");
      });
    });
    document.addEventListener("click", function (e) {
      if (!langMenu.contains(e.target)) { langMenu.classList.remove("open"); langBtn.setAttribute("aria-expanded", "false"); }
    });
  }

  /* Header capsule and mobile menu ---------------------------------------------------------- */

  var head = $(".site-head");
  var menuBtn = $("#menu-btn"), mobileNav = $("#mobile-nav");
  function onScrollHead() {
    if (head) head.classList.toggle("compact", window.scrollY > 40);
  }
  window.addEventListener("scroll", onScrollHead, { passive: true });
  onScrollHead();
  if (menuBtn && mobileNav) {
    menuBtn.addEventListener("click", function () {
      var open = !mobileNav.classList.contains("open");
      mobileNav.classList.toggle("open", open);
      menuBtn.setAttribute("aria-expanded", open ? "true" : "false");
    });
    $$("a", mobileNav).forEach(function (a) {
      a.addEventListener("click", function () { mobileNav.classList.remove("open"); menuBtn.setAttribute("aria-expanded", "false"); });
    });
  }

  /* Reveal and active section --------------------------------------------------------------- */

  if ("IntersectionObserver" in window) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (en.isIntersecting) { en.target.classList.add("in"); io.unobserve(en.target); }
      });
    }, { rootMargin: "0px 0px -8% 0px", threshold: 0.08 });
    $$(".reveal").forEach(function (el) { io.observe(el); });

    var links = $$(".nav a");
    var so = new IntersectionObserver(function (entries) {
      entries.forEach(function (en) {
        if (!en.isIntersecting) return;
        links.forEach(function (a) { a.classList.toggle("active", a.getAttribute("href") === "#" + en.target.id); });
      });
    }, { rootMargin: "-45% 0px -50% 0px" });
    $$("main section[id]").forEach(function (s) { so.observe(s); });
  } else {
    $$(".reveal").forEach(function (el) { el.classList.add("in"); });
  }

  /* Parallax: a few layers move at their own speed while scrolling. One rAF per scroll burst. */

  var layers = reduceMotion ? [] : $$("[data-parallax]");
  var ticking = false;
  function parallax() {
    ticking = false;
    var vh = window.innerHeight;
    for (var i = 0; i < layers.length; i++) {
      var el = layers[i];
      var r = el.parentElement.getBoundingClientRect();
      if (r.bottom < -200 || r.top > vh + 200) continue;
      var center = r.top + r.height / 2 - vh / 2;
      var f = parseFloat(el.getAttribute("data-parallax")) || 0;
      el.style.transform = "translate3d(0," + (center * -f).toFixed(1) + "px,0)";
    }
  }
  if (layers.length) {
    window.addEventListener("scroll", function () {
      if (!ticking) { ticking = true; requestAnimationFrame(parallax); }
    }, { passive: true });
    window.addEventListener("resize", parallax);
    parallax();
  }

  /* Tab bar demo ---------------------------------------------------------------------------- */

  // Damped spring with the same parameters as the app (Compose spring: mass 1).
  function Spring(value, stiffness, damping) {
    this.value = value; this.target = value; this.velocity = 0;
    this.k = stiffness; this.c = 2 * damping * Math.sqrt(stiffness);
  }
  Spring.prototype.step = function (dt) {
    var n = Math.max(1, Math.ceil(dt / (1 / 240)));
    var h = dt / n;
    for (var i = 0; i < n; i++) {
      var a = -this.k * (this.value - this.target) - this.c * this.velocity;
      this.velocity += a * h;
      this.value += this.velocity * h;
    }
  };
  Spring.prototype.settled = function () {
    return Math.abs(this.value - this.target) < 0.0005 && Math.abs(this.velocity) < 0.001;
  };

  function TabBar(root, opts) {
    this.root = root;
    this.wrap = root.parentElement;
    this.screen = root.closest(".navscreen") || this.wrap.parentElement;
    this.lens = $(".lens", root);
    this.tabs = $$(".tab", root);
    this.labels = this.tabs.map(function (t) { return $("span", t); });
    this.orb = $(".orb", this.wrap);
    this.count = this.tabs.length;
    this.selected = 0;
    this.pos = new Spring(0, 420, 0.66);
    this.collapse = new Spring(0, 420, 0.82);
    this.dragging = false;
    this.running = false;
    this.manual = !!(opts && opts.manual);
    this.last = 0;
    var self = this;
    this.tabs.forEach(function (t, i) {
      t.addEventListener("click", function () { if (!self.suppressClick) self.select(i); });
    });
    this.bindDrag();
    this.layout();
    window.addEventListener("resize", function () { self.layout(); });
  }
  TabBar.prototype.select = function (i) {
    this.selected = i;
    this.pos.target = i;
    this.tabs.forEach(function (t, j) {
      t.classList.toggle("on", j === i);
      t.setAttribute("aria-selected", j === i ? "true" : "false");
    });
    this.kick();
  };
  TabBar.prototype.setCollapsed = function (c) {
    var t = c ? 1 : 0;
    if (this.collapse.target !== t) { this.collapse.target = t; this.kick(); }
  };
  TabBar.prototype.bindDrag = function () {
    var self = this, startX = 0, startPos = 0, moved = false;
    this.root.addEventListener("pointerdown", function (e) {
      self.dragging = true; moved = false; startX = e.clientX; startPos = self.pos.value;
      self.root.setPointerCapture && self.root.setPointerCapture(e.pointerId);
    });
    this.root.addEventListener("pointermove", function (e) {
      if (!self.dragging) return;
      var dx = e.clientX - startX;
      if (Math.abs(dx) > 4) moved = true;
      if (!moved) return;
      var p = Math.min(self.count - 1, Math.max(0, startPos + dx / self.tabW));
      self.pos.value = p; self.pos.target = p; self.pos.velocity = 0;
      self.kick();
    });
    function end() {
      if (!self.dragging) return;
      self.dragging = false;
      if (moved) {
        self.suppressClick = true;
        setTimeout(function () { self.suppressClick = false; }, 50);
        self.select(Math.round(self.pos.value));
      }
    }
    this.root.addEventListener("pointerup", end);
    this.root.addEventListener("pointercancel", end);
  };
  TabBar.prototype.kick = function () {
    if (this.manual || this.running) return;
    this.running = true; this.last = 0;
    var self = this;
    requestAnimationFrame(function loop(ts) {
      var dt = self.last ? Math.min(0.05, (ts - self.last) / 1000) : 1 / 60;
      self.last = ts;
      self.advance(dt);
      if (self.dragging || !self.pos.settled() || !self.collapse.settled()) requestAnimationFrame(loop);
      else { self.running = false; }
    });
  };
  // Moves the springs forward by dt seconds and redraws (used directly by the video renderer).
  TabBar.prototype.advance = function (dt) {
    if (!this.dragging) this.pos.step(dt);
    this.collapse.step(dt);
    this.layout();
  };
  TabBar.prototype.layout = function () {
    var W = this.screen.clientWidth || 300;
    var s = W / 360; // dp to px: the screenshots are a 360 dp wide phone
    var p = Math.min(1.08, Math.max(-0.08, this.collapse.value));
    var height = (64 + (48 - 64) * p) * s;
    var inset = 5 * s, gap = 10 * s, margin = 16 * s;
    var full = W - margin * 2 - height - gap;
    var compact = Math.min(full, (44 * this.count + 10) * s);
    var width = full + (compact - full) * p;
    this.wrap.style.padding = "0 " + margin + "px";
    this.wrap.style.gap = gap + "px";
    this.wrap.style.bottom = 10 * s + "px";
    this.root.style.width = width + "px";
    this.root.style.height = height + "px";
    if (this.orb) { this.orb.style.width = height + "px"; this.orb.style.height = height + "px"; }
    var tabW = (width - inset * 2) / this.count;
    this.tabW = tabW;
    var travel = Math.min(1, Math.abs(this.pos.value - this.pos.target));
    this.lens.style.top = inset + "px"; this.lens.style.bottom = inset + "px"; this.lens.style.left = inset + "px";
    this.lens.style.width = tabW + "px";
    this.lens.style.transform = "translate3d(" + (this.pos.value * tabW).toFixed(2) + "px,0,0) scale(" +
      (1 + travel * 0.18).toFixed(4) + "," + (1 - travel * 0.06).toFixed(4) + ")";
    var label = 1 - Math.min(1, Math.max(0, this.collapse.value));
    var alpha = Math.min(1, Math.max(0, (label - 0.4) / 0.6));
    var icon = 24 * s * 0.92;
    for (var i = 0; i < this.count; i++) {
      var l = this.labels[i];
      l.style.opacity = alpha.toFixed(3);
      l.style.fontSize = 11 * s * 0.92 + "px";
      l.style.height = (13.5 * s * label).toFixed(2) + "px";
      var svg = $("svg", this.tabs[i]);
      svg.style.width = icon + "px"; svg.style.height = icon + "px";
    }
    var orbIcon = this.orb && $("svg", this.orb);
    if (orbIcon) { orbIcon.style.width = 26 * s + "px"; orbIcon.style.height = 26 * s + "px"; }
  };
  window.YomiraTabBar = TabBar;

  var tabbarEl = $("#tabbar-demo");
  if (tabbarEl && !document.body.hasAttribute("data-video")) {
    var bar = new TabBar(tabbarEl);
    var scroller = $("#navscroll");
    var lastY = 0;
    if (scroller) {
      scroller.addEventListener("scroll", function () {
        var y = scroller.scrollTop, dy = y - lastY;
        if (y < 24) bar.setCollapsed(false);
        else if (dy > 3) bar.setCollapsed(true);
        else if (dy < -3) bar.setCollapsed(false);
        lastY = y;
      }, { passive: true });
    }
    var autoBtn = $("#demo-auto");
    var timers = [];
    if (autoBtn) {
      autoBtn.addEventListener("click", function () {
        timers.forEach(clearTimeout); timers = [];
        var seq = [[0, function () { bar.setCollapsed(false); bar.select(0); }],
          [500, function () { bar.select(1); }], [1150, function () { bar.select(2); }],
          [1800, function () { bar.select(3); }], [2450, function () { bar.select(4); }],
          [3200, function () { bar.select(0); }], [4100, function () { bar.setCollapsed(true); }],
          [5200, function () { bar.select(3); }], [6100, function () { bar.setCollapsed(false); }],
          [6800, function () { bar.select(0); }]];
        seq.forEach(function (s) { timers.push(setTimeout(s[1], s[0])); });
      });
    }
    window.addEventListener("yomira:lang", function () { bar.layout(); });
  }

  /* Guide tabs ------------------------------------------------------------------------------ */

  var guideTabs = $$("#guide-tabs [role=tab]");
  function showGuide(btn) {
    guideTabs.forEach(function (b) { b.setAttribute("aria-selected", b === btn ? "true" : "false"); });
    $$(".guide-panel").forEach(function (p) { p.classList.toggle("on", p.id === btn.getAttribute("aria-controls")); });
  }
  guideTabs.forEach(function (b, i) {
    b.addEventListener("click", function () { showGuide(b); });
    b.addEventListener("keydown", function (e) {
      if (e.key !== "ArrowDown" && e.key !== "ArrowUp" && e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
      e.preventDefault();
      var next = guideTabs[(i + (e.key === "ArrowDown" || e.key === "ArrowRight" ? 1 : guideTabs.length - 1)) % guideTabs.length];
      next.focus(); showGuide(next);
    });
  });

  /* Latest release: version and direct APK links. Falls back to the release page. ----------- */

  function applyRelease(rel) {
    if (!rel || !rel.tag_name) return;
    $$(".latest-version").forEach(function (el) { el.textContent = rel.tag_name; });
    var assets = rel.assets || [];
    $$(".apk[data-abi]").forEach(function (a) {
      var abi = a.getAttribute("data-abi");
      var hit = assets.filter(function (x) { return x.name.indexOf("-" + abi + ".apk") > 0; })[0];
      if (hit) a.href = hit.browser_download_url;
    });
  }
  function fetchJson(url) {
    return fetch(url, { headers: { Accept: "application/vnd.github+json" } }).then(function (r) {
      if (!r.ok) throw new Error(r.status);
      return r.json();
    });
  }
  if (document.body.hasAttribute("data-video")) return;
  try {
    var cached = sessionStorage.getItem("yomira-release");
    if (cached) applyRelease(JSON.parse(cached));
    else {
      fetchJson("https://api.github.com/repos/lyraEz/Yomira/releases/latest")
        .catch(function () {
          return fetchJson("https://api.github.com/repos/lyraEz/Yomira-releases/releases").then(function (list) {
            return list.filter(function (r) { return !r.prerelease && !r.draft; })[0];
          });
        })
        .then(function (rel) {
          if (!rel) return;
          var slim = { tag_name: rel.tag_name, assets: (rel.assets || []).map(function (a) { return { name: a.name, browser_download_url: a.browser_download_url }; }) };
          try { sessionStorage.setItem("yomira-release", JSON.stringify(slim)); } catch (e) { /* ignore */ }
          applyRelease(slim);
        })
        .catch(function () { /* keep the release page links */ });
    }
  } catch (e) { /* storage blocked */ }
})();
