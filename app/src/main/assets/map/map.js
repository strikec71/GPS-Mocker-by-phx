"use strict";

const maxLatitude = 85.05112878;
let map;
let currentMarker;
let interactive;
let focused = false;

function mapPoint(latitude, longitude) {
    return [Math.max(-maxLatitude, Math.min(maxLatitude, latitude)), longitude];
}

function setupMap(canInteract, camera) {
    interactive = canInteract;
    focused = camera !== null;
    const center = camera ? mapPoint(camera[0], camera[1]) : [0, 0];
    map = L.map("map", {
        center,
        zoom: camera ? camera[2] : 2,
        minZoom: 2,
        maxZoom: 19,
        maxBounds: [[-maxLatitude, -1080], [maxLatitude, 1080]],
        maxBoundsViscosity: 1,
        worldCopyJump: true,
        attributionControl: false,
        zoomControl: interactive,
        dragging: interactive,
        touchZoom: interactive,
        doubleClickZoom: interactive,
        scrollWheelZoom: interactive,
        boxZoom: interactive,
        keyboard: interactive
    });

    const tileError = document.getElementById("tile-error");
    let tilesLoaded = false;
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
        keepBuffer: 1
    }).on("loading", () => { tilesLoaded = false; })
      .on("tileload", () => { tilesLoaded = true; tileError.hidden = true; })
      .on("load", () => { tileError.hidden = tilesLoaded; })
      .addTo(map);

    if (interactive) {
        map.getContainer().addEventListener("pointerdown", () => { focused = true; });
        map.getContainer().addEventListener("wheel", () => { focused = true; });
        map.on("moveend", () => AndroidMap.cameraChanged(...cameraPosition()));
    }
}

function showPosition(position, mocked) {
    if (currentMarker) {
        currentMarker.remove();
        currentMarker = null;
    }
    if (!position) return;

    const point = mapPoint(...position);
    currentMarker = L.circleMarker(point, {
        radius: 7,
        color: "#fff",
        weight: 2,
        fillColor: mocked ? "#b3261e" : "#1769aa",
        fillOpacity: 1,
        interactive: false
    }).addTo(map);
    if (!interactive || !focused) {
        focused = true;
        map.setView(point, 15, { animate: false });
    }
}

function showSelection(selecting) {
    document.getElementById("pin").hidden = !selecting;
}

function cameraPosition() {
    const point = map.getCenter().wrap();
    return [Math.max(-maxLatitude, Math.min(maxLatitude, point.lat)), point.lng, map.getZoom()];
}

function selectedPosition() {
    map.stop();
    return cameraPosition().slice(0, 2);
}

function centerOn(latitude, longitude) {
    focused = true;
    map.setView(mapPoint(latitude, longitude), Math.max(map.getZoom(), 16), { animate: false });
}
