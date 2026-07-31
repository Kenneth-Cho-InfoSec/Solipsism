package com.krystelligence.solipsism.userscript

object UserScriptTemplate {
    val SOURCE = """
        // ==UserScript==
        // @name         My userscript
        // @namespace    solipsism.local
        // @version      1.0.0
        // @description  Describe what this script does
        // @match        https://example.com/*
        // @grant        none
        // @run-at       document-end
        // ==/UserScript==

        (function () {
            'use strict';

            // Write your code here.
        })();
    """.trimIndent()
}
