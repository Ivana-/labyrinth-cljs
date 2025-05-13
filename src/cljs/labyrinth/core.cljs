(ns labyrinth.core
  (:require [reagent.core :as r]
            ;; [reagent.dom :as rd]
            [reagent.dom.client :as rdc]
            [labyrinth.config :as config]
            [labyrinth.views :as views]))

;; npx create-cljs-project my-app-name
;; npm add react
;; npx shadow-cljs watch client
;; npx shadow-cljs release client

(defn- dev-setup []
  (when config/debug?
    (enable-console-print!)
    (println "dev mode")))

;; https://react.dev/blog/2022/03/08/react-18-upgrade-guide#updates-to-client-rendering-apis
;; https://stackoverflow.com/questions/72389560/how-to-rerender-reagent-ui-with-react-18-and-shadow-cljs-reload
;; https://github.com/schnaq/cljs-reagent-template/blob/main/src/main/playground/core.cljs  Downgrade to react 17
;; https://github.com/reagent-project/reagent/blob/master/demo/sitetools/core.cljs

(defonce root (rdc/create-root (.getElementById js/document "app")))

(defn ^:dev/after-load mount-root []
  ;; React 17
  ;; (let [root-el (.getElementById js/document "app")]
  ;;   (rd/unmount-component-at-node root-el)
  ;;   (rd/render [views/main-component] root-el))
  ;; React 18
  ;; (.unmount root) ???
  (rdc/render root (r/as-element [views/main-component])))

(defn ^:export init []
  (dev-setup)
  (mount-root))
