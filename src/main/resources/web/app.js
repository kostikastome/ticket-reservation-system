"use strict";

const MAX_PASSENGERS = 9;
const BAGGAGE_FEES = {ECONOMY: "€15", FIRST: "€25"};
const FARES = [
    {
        id: "ECONOMY", name: "Economy", priceKey: "economyPrice",
        perks: ["Standard seat", "1 small cabin bag", "Checked bag from €15"],
    },
    {
        id: "FIRST", name: "First", priceKey: "firstClassPrice", badge: "Most comfort",
        perks: ["Front-row seat with extra legroom", "Large cabin bag", "Priority boarding", "Checked bag from €25"],
    },
];

const state = {
    token: sessionStorage.getItem("token"),
    email: sessionStorage.getItem("email"),
    tripType: "ROUND_TRIP",
    origin: null,
    destination: null,
    outboundDate: null,
    returnDate: null,
    outbound: null,
    inbound: null,
    fareClass: "ECONOMY",
    passengers: [emptyPassenger()],
    baggage: false,
    quoteRequest: 0,
};

const $ = (id) => document.getElementById(id);

/** Creates a DOM element. Text is always added as text nodes, never as HTML, so data cannot inject markup. */
function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (value === null || value === undefined || value === false) continue;
        if (key === "class") node.className = value;
        else if (key === "dataset") Object.assign(node.dataset, value);
        else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
        else if (key in node && key !== "list") node[key] = value;
        else node.setAttribute(key, value);
    }
    for (const child of children.flat()) {
        if (child === null || child === undefined || child === false) continue;
        node.append(child instanceof Node ? child : document.createTextNode(String(child)));
    }
    return node;
}

function svgIcon(pathData) {
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", "0 0 24 24");
    svg.setAttribute("aria-hidden", "true");
    const path = document.createElementNS("http://www.w3.org/2000/svg", "path");
    path.setAttribute("d", pathData);
    svg.append(path);
    return svg;
}

const PLANE = "M21 16v-2l-8-5V3.5a1.5 1.5 0 0 0-3 0V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z";

function clear(node, ...children) {
    node.replaceChildren(...children.flat().filter(Boolean));
}

function emptyPassenger() {
    return {firstName: "", lastName: "", phoneNumber: ""};
}

function money(m, digits = 2) {
    return new Intl.NumberFormat("en-IE", {
        style: "currency", currency: m.currency, minimumFractionDigits: digits, maximumFractionDigits: digits,
    }).format(Number(m.amount));
}

/** "€49" for whole amounts, "€49.50" otherwise. */
function shortMoney(m) {
    return money(m, Number(m.amount) % 1 === 0 ? 0 : 2);
}

function parseDate(isoDate) {
    const [y, m, d] = isoDate.slice(0, 10).split("-").map(Number);
    return new Date(y, m - 1, d);
}

const fmtChip = new Intl.DateTimeFormat("en-GB", {weekday: "short", day: "numeric", month: "short"});
const fmtDay = new Intl.DateTimeFormat("en-GB", {weekday: "short", day: "numeric", month: "short"});
const fmtLongDay = new Intl.DateTimeFormat("en-GB", {weekday: "long", day: "numeric", month: "long"});

function time(isoDateTime) {
    return isoDateTime.slice(11, 16);
}

function duration(minutes) {
    return `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, "0")}m`;
}

function artClass(code) {
    let hash = 0;
    for (const ch of code) hash = (hash * 31 + ch.charCodeAt(0)) % 997;
    return `art-${hash % 8}`;
}

class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

async function api(method, path, body) {
    const headers = {Accept: "application/json"};
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (state.token) headers.Authorization = `Bearer ${state.token}`;
    const response = await fetch(path, {method, headers, body: body === undefined ? undefined : JSON.stringify(body)});
    if (response.status === 204) return null;
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        if (response.status === 401 && state.token && path !== "/api/auth/login") {
            setSession(null, null);
        }
        throw new ApiError(response.status, data.error || `Request failed (${response.status})`);
    }
    return data;
}

let toastTimer;

function toast(message, isError = false) {
    const t = $("toast");
    t.textContent = message;
    t.classList.toggle("error", isError);
    t.classList.add("show");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => t.classList.remove("show"), 4000);
}

function showError(error) {
    toast(error.message || "Something went wrong", true);
}

function showView(name) {
    for (const view of document.querySelectorAll(".view")) {
        view.classList.toggle("hidden", view.id !== `view-${name}`);
    }
    for (const link of document.querySelectorAll(".nav-link")) {
        link.classList.toggle("active", link.dataset.view === name || (name === "confirmation" && link.dataset.view === "book"));
    }
    window.scrollTo({top: 0});
    if (name === "trips") loadTrips();
    if (name === "flights") loadFlights();
}

function setProgress(step) {
    for (const li of document.querySelectorAll("[data-progress]")) {
        const n = Number(li.dataset.progress);
        li.classList.toggle("active", n === step);
        li.classList.toggle("done", n < step);
    }
}

function setSession(token, email) {
    state.token = token;
    state.email = email;
    if (token) {
        sessionStorage.setItem("token", token);
        sessionStorage.setItem("email", email);
    } else {
        sessionStorage.removeItem("token");
        sessionStorage.removeItem("email");
    }
    renderAccount();
}

function renderAccount() {
    const account = $("account");
    if (state.token) {
        clear(account,
            el("span", {class: "avatar", "aria-hidden": "true"}, state.email.charAt(0)),
            el("span", {class: "account-email"}, state.email),
            el("button", {class: "btn outline-light small", onclick: logout}, "Log out"));
    } else {
        clear(account,
            el("button", {class: "btn outline-light small", onclick: () => openAuth("login")}, "Log in"),
            el("button", {class: "btn light small", onclick: () => openAuth("register")}, "Sign up"));
    }
}

async function logout() {
    try {
        await api("POST", "/api/auth/logout");
    } catch (ignored) {
        // The session is dropped locally either way.
    }
    setSession(null, null);
    toast("You have been logged out.");
    if (!$("view-trips").classList.contains("hidden")) loadTrips();
}

let authMode = "login";
let afterLogin = null;

function openAuth(mode, hint, onSuccess) {
    afterLogin = onSuccess || null;
    setAuthMode(mode);
    $("auth-hint").textContent = hint || "";
    $("auth-hint").classList.toggle("hidden", !hint);
    $("auth-error").textContent = "";
    $("auth-password").value = "";
    $("auth-password2").value = "";
    $("auth-dialog").showModal();
    $("auth-email").focus();
}

function setAuthMode(mode) {
    authMode = mode;
    for (const b of document.querySelectorAll("[data-auth]")) b.classList.toggle("active", b.dataset.auth === mode);
    $("confirm-field").classList.toggle("hidden", mode !== "register");
    $("auth-title").textContent = mode === "login" ? "Welcome back" : "Create your account";
    $("auth-submit").textContent = mode === "login" ? "Log in" : "Create account";
    $("auth-password").autocomplete = mode === "login" ? "current-password" : "new-password";
}

async function submitAuth(event) {
    event.preventDefault();
    if (event.submitter && event.submitter.value === "cancel") {
        $("auth-dialog").close();
        return;
    }
    const email = $("auth-email").value.trim();
    const password = $("auth-password").value;
    $("auth-error").textContent = "";
    $("auth-submit").disabled = true;
    try {
        const result = authMode === "login"
            ? await api("POST", "/api/auth/login", {email, password})
            : await api("POST", "/api/auth/register", {email, password, passwordConfirmation: $("auth-password2").value});
        setSession(result.token, result.email);
        $("auth-dialog").close();
        toast(authMode === "login" ? `Welcome back, ${result.email}!` : "Account created. You're logged in.");
        const next = afterLogin;
        afterLogin = null;
        if (next) next();
        else if (!$("view-trips").classList.contains("hidden")) loadTrips();
    } catch (error) {
        $("auth-error").textContent = error.message;
    } finally {
        $("auth-submit").disabled = false;
    }
}

let airportsByCode = {};

function resetDestinationSelect() {
    clear($("destination"), el("option", {value: "", disabled: true, selected: true}, "Choose departure first"));
    $("destination").disabled = true;
    $("search-btn").disabled = true;
}

async function loadOrigins() {
    const origins = await api("GET", "/api/airports/origins");
    for (const a of origins) airportsByCode[a.code] = a;
    clear($("origin"),
        el("option", {value: "", disabled: true, selected: true}, "Where from?"),
        origins.map((a) => el("option", {value: a.code}, `${a.city} (${a.code})`)));
}

async function loadDestinations(origin) {
    const destinations = await api("GET", `/api/airports/${origin}/destinations`);
    for (const a of destinations) airportsByCode[a.code] = a;
    clear($("destination"),
        el("option", {value: "", disabled: true, selected: true}, "Where to?"),
        destinations.map((a) => el("option", {value: a.code}, `${a.city} (${a.code})`)));
    $("destination").disabled = false;
}

async function onOriginChange() {
    state.origin = $("origin").value || null;
    state.destination = null;
    resetDestinationSelect();
    if (state.origin) {
        await loadDestinations(state.origin);
        $("destination").focus();
    }
}

function onDestinationChange() {
    state.destination = $("destination").value || null;
    $("search-btn").disabled = !state.destination;
}

async function swapAirports() {
    if (!state.origin || !state.destination) return;
    const [from, to] = [state.destination, state.origin];
    $("origin").value = from;
    state.origin = from;
    await loadDestinations(from);
    if ([...$("destination").options].some((o) => o.value === to)) {
        $("destination").value = to;
        onDestinationChange();
        if (!$("booking").classList.contains("hidden")) await startSearch();
    } else {
        state.destination = null;
        $("search-btn").disabled = true;
        toast(`There are no flights from ${from} to ${to}.`, true);
    }
}

async function startSearch(preferredDate) {
    if (!state.origin || !state.destination) return;
    $("home").classList.add("hidden");
    $("booking").classList.remove("hidden");
    setProgress(1);
    resetFrom("route");
    const from = airportsByCode[state.origin];
    const to = airportsByCode[state.destination];
    $("outbound-title").textContent = `${from.city} → ${to.city}`;
    $("outbound-sub").textContent = "Pick a date, then a flight. Prices are the lowest Economy fare that day; green marks the cheapest days.";
    await loadOutboundDates(preferredDate);
    $("booking").scrollIntoView({behavior: "smooth", block: "start"});
}

/** Clears everything that depends on a choice that just changed. */
function resetFrom(level) {
    if (level === "route") {
        state.outboundDate = null;
        clear($("outbound-dates"));
        clear($("outbound-flights"));
    }
    if (level === "route" || level === "outbound") {
        state.outbound = null;
        state.returnDate = null;
        clear($("return-dates"));
        clear($("return-flights"));
        $("step-return").classList.add("hidden");
    }
    if (level === "route" || level === "outbound" || level === "return") {
        state.inbound = null;
        $("step-details").classList.add("hidden");
        setProgress(1);
    }
}

function renderDateStrip(container, fares, selectedDate, onPick) {
    const cheapest = fares.reduce((min, f) => (min === null || Number(f.lowestFare.amount) < min ? Number(f.lowestFare.amount) : min), null);
    clear(container, fares.map((f) => el("button", {
            type: "button",
            class: `date-chip${f.date === selectedDate ? " active" : ""}${Number(f.lowestFare.amount) === cheapest ? " cheapest" : ""}`,
            "aria-pressed": String(f.date === selectedDate),
            onclick: () => onPick(f.date),
        },
        el("span", {class: "d"}, fmtChip.format(parseDate(f.date))),
        el("span", {class: "p"}, shortMoney(f.lowestFare)))));
    const active = container.querySelector(".active");
    if (active) active.scrollIntoView({block: "nearest", inline: "center"});
}

async function loadOutboundDates(preferredDate) {
    const fares = await api("GET", `/api/routes/${state.origin}/${state.destination}/dates`);
    if (fares.length === 0) {
        clear($("outbound-dates"), el("div", {class: "empty"}, "No upcoming flights on this route."));
        return;
    }
    const pick = async (date) => {
        state.outboundDate = date;
        renderDateStrip($("outbound-dates"), fares, date, pick);
        resetFrom("outbound");
        try {
            await loadOutboundFlights();
        } catch (error) {
            showError(error);
        }
    };
    const initial = fares.some((f) => f.date === preferredDate) ? preferredDate : fares[0].date;
    await pick(initial);
}

async function loadOutboundFlights() {
    const flights = await api("GET",
        `/api/flights/search?from=${state.origin}&to=${state.destination}&date=${state.outboundDate}`);
    renderFlightList($("outbound-flights"), flights, () => state.outbound, async (flight) => {
        resetFrom("outbound");
        state.outbound = flight;
        if (state.tripType === "ROUND_TRIP") await loadReturnDates();
        else showDetails();
    });
}

async function loadReturnDates() {
    const options = await api("GET", `/api/flights/${state.outbound.id}/returns`);
    const byDate = new Map();
    for (const f of options.filter((o) => o.availableSeats > 0)) {
        const date = f.departure.slice(0, 10);
        const current = byDate.get(date);
        if (!current || Number(f.economyPrice.amount) < Number(current.amount)) byDate.set(date, f.economyPrice);
    }
    const fares = [...byDate].map(([date, lowestFare]) => ({date, lowestFare}));
    $("step-return").classList.remove("hidden");
    $("return-title").textContent = `${state.outbound.destination.city} → ${state.outbound.origin.city}`;
    $("return-sub").textContent = "When are you coming back?";
    if (fares.length === 0) {
        clear($("return-dates"), el("div", {class: "empty"}, "No return flights available after this departure."));
        return;
    }
    const pick = (date) => {
        state.returnDate = date;
        renderDateStrip($("return-dates"), fares, date, pick);
        resetFrom("return");
        renderFlightList($("return-flights"), options.filter((f) => f.departure.startsWith(date)),
            () => state.inbound, (flight) => {
                state.inbound = flight;
                showDetails();
            });
    };
    const outboundDay = parseDate(state.outbound.departure);
    const weekLater = fares.find((f) => (parseDate(f.date) - outboundDay) / 86400000 >= 3);
    pick((weekLater || fares[0]).date);
    $("step-return").scrollIntoView({behavior: "smooth", block: "start"});
}

function renderFlightList(container, flights, getSelected, onSelect) {
    if (flights.length === 0) {
        clear(container, el("div", {class: "empty"}, "No flights on this day."));
        return;
    }
    const render = () => clear(container, flights.map((f) => flightCard(f, getSelected()?.id === f.id, async () => {
        try {
            await onSelect(f);
            render();
        } catch (error) {
            showError(error);
        }
    })));
    render();
}

function flightCard(f, selected, onClick) {
    const full = f.availableSeats === 0;
    const low = !full && f.availableSeats <= 9;
    return el("button", {type: "button", class: `flight${selected ? " selected" : ""}`, disabled: full, onclick: onClick, "aria-pressed": String(selected)},
        el("div", {class: "flight-main"},
            el("div", {class: "flight-times"},
                el("div", {}, el("div", {class: "time"}, time(f.departure)), el("div", {class: "code"}, `${f.origin.city} ${f.origin.code}`)),
                el("div", {class: "leg"}, duration(f.durationMinutes), el("div", {class: "leg-line"}, svgIcon(PLANE)), "Direct"),
                el("div", {}, el("div", {class: "time"}, time(f.arrival)), el("div", {class: "code"}, `${f.destination.city} ${f.destination.code}`))),
            el("div", {class: "flight-meta"},
                el("span", {}, `${f.flightNumber} · ${f.aircraft}`),
                el("span", {class: `pill${full ? " full" : low ? " low" : ""}`},
                    full ? "Sold out" : low ? `Only ${f.availableSeats} seats left` : `${f.availableSeats} seats left`))),
        el("div", {class: "fare-col"}, el("small", {}, "Economy"), el("strong", {}, shortMoney(f.economyPrice))),
        el("div", {class: "fare-col first"}, el("small", {}, "First"), el("strong", {}, shortMoney(f.firstClassPrice))));
}

function maxPassengers() {
    const seats = Math.min(state.outbound.availableSeats, state.inbound ? state.inbound.availableSeats : Infinity);
    return Math.min(MAX_PASSENGERS, seats);
}

function showDetails() {
    $("step-details").classList.remove("hidden");
    setProgress(2);
    state.passengers = state.passengers.slice(0, Math.max(1, maxPassengers()));
    renderFares();
    renderOptions();
    renderPassengers();
    renderSummary();
    updateQuote();
    $("step-details").scrollIntoView({behavior: "smooth", block: "start"});
}

function renderFares() {
    const flights = [state.outbound, state.inbound].filter(Boolean);
    clear($("fares"), FARES.map((fare) => {
        const perPerson = flights.reduce((sum, f) => sum + Number(f[fare.priceKey].amount), 0);
        const currency = flights[0][fare.priceKey].currency;
        return el("button", {
                type: "button", role: "radio", class: `fare${state.fareClass === fare.id ? " active" : ""}`,
                "aria-checked": String(state.fareClass === fare.id),
                onclick: () => {
                    state.fareClass = fare.id;
                    renderFares();
                    renderOptions();
                    updateQuote();
                },
            },
            fare.badge && el("span", {class: "fare-badge"}, fare.badge),
            el("span", {class: "fare-name"}, fare.name),
            el("span", {class: "fare-price"}, shortMoney({amount: String(perPerson), currency}),
                el("small", {}, flights.length > 1 ? " / person, return" : " / person")),
            el("ul", {}, fare.perks.map((p) => el("li", {}, p))));
    }));
}

function renderOptions() {
    $("pax-count").textContent = state.passengers.length;
    $("pax-minus").disabled = state.passengers.length <= 1;
    $("pax-plus").disabled = state.passengers.length >= maxPassengers();
    $("baggage").checked = state.baggage;
    $("bag-fee").textContent = `${BAGGAGE_FEES[state.fareClass]} per passenger, per flight`;
}

const NAME_PATTERN = /^\p{L}[\p{L} '\-]{0,49}$/u;
const PHONE_PATTERN = /^\+?\d{8,15}$/;

function validField(key, value) {
    const v = value.trim();
    if (key === "phoneNumber") return PHONE_PATTERN.test(v.replace(/[\s\-()]/g, ""));
    return NAME_PATTERN.test(v.replace(/\s+/g, " "));
}

function renderPassengers() {
    const fields = [
        ["firstName", "First name", "given-name", "text"],
        ["lastName", "Last name", "family-name", "text"],
        ["phoneNumber", "Phone", "tel", "tel"],
    ];
    clear($("passenger-forms"), state.passengers.map((p, i) =>
        el("div", {class: "passenger"},
            el("h4", {}, el("span", {}, i + 1), i === 0 ? "Lead passenger" : `Passenger ${i + 1}`),
            el("div", {class: "passenger-grid"}, fields.map(([key, label, autocomplete, type]) =>
                el("label", {class: "field"},
                    el("span", {}, label),
                    el("input", {
                        type, autocomplete: i === 0 ? autocomplete : "off", value: p[key],
                        placeholder: key === "phoneNumber" ? "+355 69 123 4567" : "",
                        dataset: {passenger: String(i), key},
                        oninput: (e) => {
                            p[key] = e.target.value;
                            e.target.classList.remove("invalid");
                        },
                        onblur: (e) => e.target.classList.toggle("invalid", e.target.value !== "" && !validField(key, e.target.value)),
                    })))))));
}

function renderSummary() {
    const leg = (label, f) => el("div", {class: "summary-leg"},
        el("span", {class: "tag"}, label),
        el("strong", {}, `${f.origin.code} → ${f.destination.code}`),
        el("span", {}, `${fmtDay.format(parseDate(f.departure))} · ${time(f.departure)}–${time(f.arrival)}`));
    clear($("summary-flights"), leg("Out", state.outbound), state.inbound && leg("Back", state.inbound));
}

function tripPayload() {
    return {
        outboundFlightId: state.outbound.id,
        returnFlightId: state.inbound ? state.inbound.id : null,
        fareClass: state.fareClass,
        checkedBaggage: state.baggage,
    };
}

async function updateQuote() {
    const requestId = ++state.quoteRequest;
    try {
        const quote = await api("POST", "/api/quote", {...tripPayload(), passengerCount: state.passengers.length});
        if (requestId !== state.quoteRequest) return; // a newer request already answered
        clear($("quote-lines"), quote.lines.map((l) =>
            el("div", {class: "quote-line"},
                el("span", {}, `${l.description.replace(/ TR\d+/, "")}${l.quantity > 1 ? ` × ${l.quantity}` : ""}`),
                el("span", {}, money(l.amount)))));
        $("quote-total").textContent = money(quote.total);
    } catch (error) {
        showError(error);
    }
}

function checkPassengerForms() {
    let firstInvalid = null;
    for (const input of document.querySelectorAll("#passenger-forms input")) {
        const ok = validField(input.dataset.key, input.value);
        input.classList.toggle("invalid", !ok);
        if (!ok && !firstInvalid) firstInvalid = input;
    }
    if (firstInvalid) {
        firstInvalid.focus();
        toast("Please check the highlighted passenger details.", true);
        return false;
    }
    return true;
}

async function book() {
    if (!checkPassengerForms()) return;
    if (!state.token) {
        openAuth("login", "Log in or create an account to finish your booking.", book);
        return;
    }
    const button = $("book-btn");
    button.disabled = true;
    try {
        const booking = await api("POST", "/api/bookings", {...tripPayload(), passengers: state.passengers});
        showConfirmation(booking);
    } catch (error) {
        if (error.status === 401) {
            openAuth("login", "Your session expired. Please log in again.", book);
        } else {
            showError(error);
            if (error.status === 409) await refreshSelectedFlights();
        }
    } finally {
        button.disabled = false;
    }
}

/** After a "sold out" conflict, reload seat counts so the page shows what is really left. */
async function refreshSelectedFlights() {
    if (state.outboundDate) await loadOutboundFlights();
}

function barcode(text) {
    let seed = 0;
    for (const ch of text) seed = (seed * 131 + ch.charCodeAt(0)) % 100003;
    const bars = [];
    for (let i = 0; i < 34; i++) {
        seed = (seed * 9301 + 49297) % 233280;
        bars.push(el("i", {class: `w${1 + (seed % 3)}`}));
    }
    return el("div", {class: "barcode", "aria-hidden": "true"}, bars);
}

function boardingPass(booking) {
    const legs = [booking.outbound, booking.return].filter(Boolean);
    const first = legs[0];
    const names = booking.passengers.map((p) => `${p.firstName} ${p.lastName}`);
    const status = booking.status.toLowerCase();
    return el("article", {class: `pass ${status}`},
        el("div", {class: "pass-main"},
            el("div", {class: "pass-top"},
                el("span", {class: "pass-airline"}, svgIcon(PLANE), booking.return ? "Return trip" : "One way"),
                el("span", {class: `badge ${status}`}, booking.status)),
            el("div", {class: "pass-legs"}, legs.map((f) => el("div", {class: "pass-route"},
                el("div", {}, el("div", {class: "pass-code"}, f.origin.code), el("div", {class: "pass-city"}, `${f.origin.city} · ${time(f.departure)}`)),
                el("div", {class: "pass-path"}, svgIcon(PLANE)),
                el("div", {class: "right"}, el("div", {class: "pass-code"}, f.destination.code), el("div", {class: "pass-city"}, `${f.destination.city} · ${time(f.arrival)}`))))),
            el("div", {class: "pass-info"},
                el("div", {}, el("small", {}, "Date"), el("b", {}, fmtDay.format(parseDate(first.departure)))),
                el("div", {}, el("small", {}, "Flight"), el("b", {}, legs.map((f) => f.flightNumber).join(" / "))),
                el("div", {}, el("small", {}, "Class"), el("b", {}, booking.fareClass === "FIRST" ? "First" : "Economy")),
                el("div", {}, el("small", {}, "Bags"), el("b", {}, booking.checkedBaggage ? "Checked" : "Cabin only"))),
            el("div", {class: "pass-actions"},
                el("span", {}, names.join(", ")),
                booking.cancellable && el("button", {class: "btn secondary small", onclick: () => cancelTrip(booking)}, "Cancel booking"),
                !booking.cancellable && booking.status === "CONFIRMED" && el("span", {}, "Within 24 h of departure: can't be cancelled online"))),
        el("div", {class: "pass-stub"},
            el("div", {}, el("small", {}, "Booking reference"), el("div", {class: "pass-ref"}, booking.reference)),
            el("div", {}, el("small", {}, `${names.length} passenger${names.length > 1 ? "s" : ""} · total`), el("div", {class: "pass-total"}, money(booking.totalPrice))),
            barcode(booking.reference)));
}

function showConfirmation(booking) {
    setProgress(3);
    clear($("confirm-passes"), boardingPass(booking));
    clear($("confirm-total"), el("span", {}, "Total paid"), el("strong", {}, money(booking.totalPrice)));
    showView("confirmation");
}

function startOver() {
    state.passengers = [emptyPassenger()];
    state.baggage = false;
    state.fareClass = "ECONOMY";
    state.origin = state.destination = null;
    $("origin").value = "";
    resetDestinationSelect();
    resetFrom("route");
    $("booking").classList.add("hidden");
    $("home").classList.remove("hidden");
    showView("book");
    loadDeals();
}

async function loadDeals() {
    const container = $("deals");
    if (!container.children.length) clear(container, [1, 2, 3, 4].map(() => el("div", {class: "skeleton"})));
    try {
        const deals = await api("GET", "/api/deals");
        clear(container, deals.map((f) => el("button", {
                type: "button", class: "deal",
                onclick: () => openDeal(f).catch(showError),
            },
            el("div", {class: `deal-art ${artClass(f.destination.code)}`},
                el("span", {class: "deal-code", "aria-hidden": "true"}, f.destination.code),
                el("span", {class: "deal-city"}, f.destination.city),
                el("span", {class: "deal-country"}, f.destination.country)),
            el("div", {class: "deal-body"},
                el("div", {class: "deal-route"},
                    el("b", {}, `from ${f.origin.city}`), el("br"), fmtLongDay.format(parseDate(f.departure))),
                el("div", {class: "deal-price"}, el("small", {}, "one way from"), el("strong", {}, shortMoney(f.economyPrice)))))));
    } catch (error) {
        clear(container);
        showError(error);
    }
}

async function openDeal(flight) {
    state.tripType = "ONE_WAY";
    syncTripTabs();
    $("origin").value = flight.origin.code;
    state.origin = flight.origin.code;
    await loadDestinations(flight.origin.code);
    $("destination").value = flight.destination.code;
    onDestinationChange();
    await startSearch(flight.departure.slice(0, 10));
}

function syncTripTabs() {
    for (const tab of document.querySelectorAll("[data-trip]")) {
        const active = tab.dataset.trip === state.tripType;
        tab.classList.toggle("active", active);
        tab.setAttribute("aria-checked", String(active));
    }
}

async function loadTrips() {
    const container = $("trips");
    if (!state.token) {
        clear(container, el("div", {class: "empty"},
            el("p", {}, "Log in to see your trips."),
            el("button", {class: "btn cta", onclick: () => openAuth("login", "Log in to see your trips.")}, "Log in")));
        return;
    }
    try {
        const trips = await api("GET", "/api/bookings");
        if (trips.length === 0) {
            clear(container, el("div", {class: "empty"},
                el("p", {}, "No trips yet. Your next adventure is a few clicks away."),
                el("button", {class: "btn cta", onclick: () => showView("book")}, "Find a flight")));
            return;
        }
        clear(container, trips.map(boardingPass));
    } catch (error) {
        if (error.status === 401) loadTrips();
        else showError(error);
    }
}

function cancelTrip(booking) {
    $("confirm-title").textContent = `Cancel booking ${booking.reference}?`;
    $("confirm-text").textContent = `${money(booking.totalPrice)} will be refunded and the seats released. This cannot be undone.`;
    const dialog = $("confirm-dialog");
    dialog.returnValue = "";
    dialog.onclose = async () => {
        if (dialog.returnValue !== "yes") return;
        try {
            await api("POST", `/api/bookings/${booking.reference}/cancel`);
            toast(`Booking ${booking.reference} cancelled.`);
            loadTrips();
        } catch (error) {
            showError(error);
        }
    };
    dialog.showModal();
}

const MAX_TABLE_ROWS = 100;
let filterTimer;

async function loadFlights() {
    try {
        const q = $("flight-filter").value.trim();
        const flights = await api("GET", `/api/flights?q=${encodeURIComponent(q)}`);
        clear($("flights-body"), flights.slice(0, MAX_TABLE_ROWS).map((f) => el("tr", {},
            el("td", {}, el("strong", {}, f.flightNumber)),
            el("td", {}, `${f.origin.city} (${f.origin.code}) → ${f.destination.city} (${f.destination.code})`),
            el("td", {}, `${fmtDay.format(parseDate(f.departure))} ${time(f.departure)}`),
            el("td", {}, time(f.arrival)),
            el("td", {}, duration(f.durationMinutes)),
            el("td", {class: "num"}, money(f.economyPrice)),
            el("td", {class: "num"}, money(f.firstClassPrice)),
            el("td", {class: "num"}, f.availableSeats === 0 ? "Full" : f.availableSeats))));
        $("flights-note").textContent = flights.length === 0 ? "No flights match your search."
            : flights.length > MAX_TABLE_ROWS ? `Showing the first ${MAX_TABLE_ROWS} of ${flights.length} flights. Search to narrow the list.`
                : `${flights.length} flight${flights.length > 1 ? "s" : ""}`;
    } catch (error) {
        showError(error);
    }
}

function bindEvents() {
    document.addEventListener("click", (e) => {
        const target = e.target.closest("[data-view]");
        if (target) {
            e.preventDefault();
            showView(target.dataset.view);
        }
        const scroller = e.target.closest("[data-scroll]");
        if (scroller) {
            const strip = $(scroller.dataset.scroll);
            strip.scrollBy({left: Number(scroller.dataset.dir) * strip.clientWidth * 0.8});
        }
    });
    $("brand").addEventListener("click", (e) => {
        e.preventDefault();
        startOver();
    });

    for (const tab of document.querySelectorAll("[data-trip]")) {
        tab.addEventListener("click", async () => {
            state.tripType = tab.dataset.trip;
            syncTripTabs();
            if (state.outbound) {
                const outbound = state.outbound;
                resetFrom("outbound");
                state.outbound = outbound;
                try {
                    if (state.tripType === "ROUND_TRIP") await loadReturnDates();
                    else showDetails();
                } catch (error) {
                    showError(error);
                }
            }
        });
    }

    const guard = (fn) => async (...args) => {
        try {
            await fn(...args);
        } catch (error) {
            showError(error);
        }
    };
    $("origin").addEventListener("change", guard(onOriginChange));
    $("destination").addEventListener("change", onDestinationChange);
    $("swap").addEventListener("click", guard(swapAirports));
    $("search-form").addEventListener("submit", guard(async (e) => {
        e.preventDefault();
        await startSearch();
    }));

    $("pax-minus").addEventListener("click", () => {
        if (state.passengers.length > 1) state.passengers.pop();
        renderOptions();
        renderPassengers();
        updateQuote();
    });
    $("pax-plus").addEventListener("click", () => {
        if (state.passengers.length < maxPassengers()) state.passengers.push(emptyPassenger());
        renderOptions();
        renderPassengers();
        updateQuote();
    });
    $("baggage").addEventListener("change", (e) => {
        state.baggage = e.target.checked;
        updateQuote();
    });
    $("book-btn").addEventListener("click", book);
    $("book-another").addEventListener("click", startOver);

    for (const b of document.querySelectorAll("[data-auth]")) {
        b.addEventListener("click", () => {
            setAuthMode(b.dataset.auth);
            $("auth-error").textContent = "";
        });
    }
    $("auth-form").addEventListener("submit", submitAuth);

    $("flight-filter").addEventListener("input", () => {
        clearTimeout(filterTimer);
        filterTimer = setTimeout(loadFlights, 250);
    });
}

async function init() {
    bindEvents();
    resetDestinationSelect();
    renderAccount();
    if (state.token) {
        try {
            await api("GET", "/api/me");
        } catch (ignored) {
            setSession(null, null);
        }
    }
    try {
        await loadOrigins();
    } catch (error) {
        showError(error);
    }
    loadDeals();
}

init();
