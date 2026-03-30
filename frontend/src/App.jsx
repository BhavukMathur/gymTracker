import { useCallback, useEffect, useMemo, useState } from "react";
import "./App.css";
import { apiBase, authHeader } from "./api.js";
import {
  clearStoredToken,
  getStoredToken,
  getUsernameFromToken,
  setStoredToken,
} from "./session.js";

const MONTHLY_GOAL = 20;

function daysInMonth(year, month) {
  return new Date(year, month, 0).getDate();
}

function formatDateKey(year, month, day) {
  return `${year}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/** Today's date in local time as YYYY-MM-DD */
function getTodayDateKey() {
  const d = new Date();
  return formatDateKey(d.getFullYear(), d.getMonth() + 1, d.getDate());
}

function getCurrentYearMonth() {
  const d = new Date();
  return { year: d.getFullYear(), month: d.getMonth() + 1 };
}

function shiftCalendarMonth(year, month, delta) {
  const d = new Date(year, month - 1 + delta, 1);
  return { year: d.getFullYear(), month: d.getMonth() + 1 };
}

function parseDateKey(key) {
  const [y, m] = key.split("-").map(Number);
  return { y, m };
}

/** Monday–Sunday of the week containing `anchor` (local time). */
function weekContaining(anchor) {
  const d = new Date(anchor);
  d.setHours(12, 0, 0, 0);
  const day = d.getDay();
  const mondayOffset = day === 0 ? -6 : 1 - day;
  const monday = new Date(d);
  monday.setDate(d.getDate() + mondayOffset);
  const labels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
  return labels.map((label, i) => {
    const x = new Date(monday);
    x.setDate(monday.getDate() + i);
    return {
      label,
      date: formatDateKey(x.getFullYear(), x.getMonth() + 1, x.getDate()),
    };
  });
}

function displayNameFromUsername(u) {
  if (!u) return "";
  const s = u.replace(/[._-]+/g, " ");
  return s
    .split(" ")
    .filter(Boolean)
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
}

function initialsFromUsername(u) {
  if (!u) return "?";
  const parts = u.replace(/[._-]+/g, " ").split(/\s+/).filter(Boolean);
  if (parts.length >= 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase();
  }
  return u.slice(0, 2).toUpperCase();
}

function levelFromPresent(count) {
  if (count >= 15) return "Dedicated";
  if (count >= 8) return "Consistent";
  if (count >= 1) return "Building momentum";
  return "Getting started";
}

const MONTH_OPTIONS = Array.from({ length: 12 }, (_, i) => {
  const month = i + 1;
  const label = new Date(2000, i, 1).toLocaleString(undefined, {
    month: "long",
  });
  return { value: month, label };
});

const WEEKDAY_HEADERS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

export default function App() {
  const [token, setToken] = useState(() => getStoredToken());
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loginMessage, setLoginMessage] = useState("");
  const [viewYear, setViewYear] = useState(() => getCurrentYearMonth().year);
  const [viewMonth, setViewMonth] = useState(() => getCurrentYearMonth().month);
  const [monthData, setMonthData] = useState({});
  const [calendarBusy, setCalendarBusy] = useState(false);
  const [calendarError, setCalendarError] = useState("");
  const [serverProfile, setServerProfile] = useState("");

  const base = apiBase();
  const todayKey = getTodayDateKey();

  const sessionUser = useMemo(() => getUsernameFromToken(token), [token]);
  const displayName = useMemo(
    () => displayNameFromUsername(sessionUser || ""),
    [sessionUser]
  );

  const clearAuth = useCallback(() => {
    clearStoredToken();
    setToken("");
    setMonthData({});
    setCalendarError("");
  }, []);

  const loadMonth = useCallback(async () => {
    if (!token) return;
    const res = await fetch(
      `${base}/api/attendance/month?year=${viewYear}&month=${viewMonth}`,
      { headers: authHeader(token) }
    );
    if (res.status === 401) {
      clearAuth();
      return;
    }
    setMonthData(res.ok ? await res.json() : {});
  }, [base, viewYear, viewMonth, token, clearAuth]);

  const login = async () => {
    const res = await fetch(`${base}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!res.ok) {
      setLoginMessage("Login failed");
      return;
    }
    const data = await res.json();
    setStoredToken(data.token);
    setToken(data.token);
    setLoginMessage("");
    setCalendarError("");
  };

  const register = async () => {
    const res = await fetch(`${base}/api/auth/register`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });
    if (!res.ok) {
      const msg =
        res.status === 409 ? "Username already taken" : "Registration failed";
      setLoginMessage(msg);
      return;
    }
    setLoginMessage("Registered. Logging in...");
    await login();
  };

  const logout = () => {
    clearAuth();
    setUsername("");
    setPassword("");
    setLoginMessage("");
  };

  const cycleAttendance = async (date) => {
    if (!token || calendarBusy) return;
    setCalendarError("");
    const current = monthData[date];
    setCalendarBusy(true);

    const fail = (msg) => setCalendarError(msg);

    try {
      if (current !== true && current !== false) {
        const res = await fetch(`${base}/api/attendance`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeader(token),
          },
          body: JSON.stringify({ date, attended: true }),
        });
        if (res.status === 401) {
          clearAuth();
          return;
        }
        if (!res.ok) {
          fail("Could not save attendance");
          return;
        }
        setMonthData((p) => ({ ...p, [date]: true }));
        return;
      }

      if (current === true) {
        const res = await fetch(`${base}/api/attendance`, {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...authHeader(token),
          },
          body: JSON.stringify({ date, attended: false }),
        });
        if (res.status === 401) {
          clearAuth();
          return;
        }
        if (!res.ok) {
          fail("Could not save attendance");
          return;
        }
        setMonthData((p) => ({ ...p, [date]: false }));
        return;
      }

      const res = await fetch(
        `${base}/api/attendance?date=${encodeURIComponent(date)}`,
        {
          method: "DELETE",
          headers: authHeader(token),
        }
      );
      if (res.status === 401) {
        clearAuth();
        return;
      }
      if (!res.ok) {
        fail("Could not clear attendance");
        return;
      }
      setMonthData((p) => {
        const next = { ...p };
        delete next[date];
        return next;
      });
    } finally {
      setCalendarBusy(false);
    }
  };

  const markTodayPresent = async () => {
    if (!token || calendarBusy) return;
    const [ty, tm] = todayKey.split("-").map(Number);
    if (ty !== viewYear || tm !== viewMonth) return;
    if (monthData[todayKey] === true) return;

    setCalendarError("");
    setCalendarBusy(true);
    try {
      const res = await fetch(`${base}/api/attendance`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...authHeader(token),
        },
        body: JSON.stringify({ date: todayKey, attended: true }),
      });
      if (res.status === 401) {
        clearAuth();
        return;
      }
      if (!res.ok) {
        setCalendarError("Could not save attendance");
        return;
      }
      setMonthData((p) => ({ ...p, [todayKey]: true }));
    } finally {
      setCalendarBusy(false);
    }
  };

  const year = viewYear;
  const month = viewMonth;
  const dayCount = daysInMonth(year, month);

  const yNow = new Date().getFullYear();
  const yearOptions = [];
  for (let i = yNow - 6; i <= yNow + 2; i++) yearOptions.push(i);

  const calendarCells = useMemo(() => {
    const leadingBlanks = new Date(year, month - 1, 1).getDay();
    const days = [];
    for (let d = 1; d <= dayCount; d++) {
      days.push({ day: d, date: formatDateKey(year, month, d) });
    }
    return { leadingBlanks, days };
  }, [year, month, dayCount]);

  const dashboardStats = useMemo(() => {
    let present = 0;
    let missed = 0;
    for (let d = 1; d <= dayCount; d++) {
      const key = formatDateKey(year, month, d);
      const v = monthData[key];
      if (v === true) present++;
      else if (v === false) missed++;
    }

    const now = new Date();
    const viewingCurrent =
      year === now.getFullYear() && month === now.getMonth() + 1;
    const endDay = viewingCurrent ? Math.min(now.getDate(), dayCount) : dayCount;
    let streak = 0;
    for (let d = endDay; d >= 1; d--) {
      const key = formatDateKey(year, month, d);
      if (monthData[key] === true) streak++;
      else break;
    }

    let run = 0;
    let bestInMonth = 0;
    for (let d = 1; d <= dayCount; d++) {
      const key = formatDateKey(year, month, d);
      if (monthData[key] === true) {
        run++;
        bestInMonth = Math.max(bestInMonth, run);
      } else {
        run = 0;
      }
    }

    return { present, missed, streak, bestInMonth };
  }, [monthData, year, month, dayCount]);

  const weekRows = useMemo(() => {
    return weekContaining(new Date()).map(({ label, date }) => {
      const { y: wy, m: wm } = parseDateKey(date);
      const inView = wy === viewYear && wm === viewMonth;
      if (!inView) {
        return { label, date, state: "na" };
      }
      const v = monthData[date];
      if (v === true) return { label, date, state: "yes" };
      if (v === false) return { label, date, state: "no" };
      return { label, date, state: "unset" };
    });
  }, [monthData, viewYear, viewMonth]);

  const monthTitle = useMemo(() => {
    const label = new Date(year, month - 1, 1).toLocaleString(undefined, {
      month: "long",
    });
    return `${label} ${year}`;
  }, [year, month]);

  const goalPct = Math.min(
    100,
    Math.round((dashboardStats.present / MONTHLY_GOAL) * 100)
  );

  const loggedIn = Boolean(token);
  const todayInView = useMemo(() => {
    const [ty, tm] = todayKey.split("-").map(Number);
    return ty === viewYear && tm === viewMonth;
  }, [todayKey, viewYear, viewMonth]);

  const canMarkToday =
    todayInView && monthData[todayKey] !== true && !calendarBusy;

  const goPrevMonth = () => {
    const n = shiftCalendarMonth(viewYear, viewMonth, -1);
    setViewYear(n.year);
    setViewMonth(n.month);
  };

  const goNextMonth = () => {
    const n = shiftCalendarMonth(viewYear, viewMonth, 1);
    setViewYear(n.year);
    setViewMonth(n.month);
  };

  useEffect(() => {
    if (!token) return;
    void loadMonth();
  }, [token, viewYear, viewMonth, loadMonth]);

  useEffect(() => {
    if (!token) {
      setServerProfile("");
      return;
    }
    let cancelled = false;
    void (async () => {
      const res = await fetch(`${base}/api/app/profile`);
      if (cancelled || !res.ok) return;
      const data = await res.json();
      if (!cancelled && typeof data.profile === "string") {
        setServerProfile(data.profile);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [token, base]);

  return (
    <div className={`app-shell ${loggedIn ? "app-shell--wide" : ""}`}>
      <header className="app-header">
        <h1>Gym Attendance</h1>
        <p className="tagline">Track workouts by day</p>
      </header>

      <main className={`app-main ${loggedIn ? "app-main--dashboard" : ""}`}>
        <div className={loggedIn ? "hidden" : "card"}>
          <h3>Login</h3>
          <form
            className="row stack-sm"
            autoComplete="off"
            onSubmit={(e) => e.preventDefault()}
          >
            <input
              name="gymtracker-username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="Username"
              type="text"
              autoComplete="off"
            />
            <input
              name="gymtracker-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              type="password"
              placeholder="Password"
              autoComplete="off"
            />
          </form>
          <div className="row btn-row">
            <button type="button" className="btn btn-primary" onClick={() => void login()}>
              Login
            </button>
            <button
              type="button"
              className="btn btn-secondary"
              onClick={() => void register()}
            >
              Register
            </button>
          </div>
          {loginMessage ? (
            <div
              className={`message ${loginMessage === "Login failed" || loginMessage.includes("failed") || loginMessage.includes("taken") ? "error" : ""}`}
            >
              {loginMessage}
            </div>
          ) : null}
        </div>

        {loggedIn ? (
          <>
            <aside className="dash-sidebar dash-sidebar--left">
              <div className="dash-card profile-card">
                <div className="profile-avatar" aria-hidden>
                  {initialsFromUsername(sessionUser || "")}
                </div>
                <p className="profile-name">{displayName || sessionUser}</p>
                <p className="profile-level">
                  Level: {levelFromPresent(dashboardStats.present)}
                </p>
                {serverProfile ? (
                  <p className="profile-env">profile={serverProfile}</p>
                ) : null}
              </div>

              <div className="dash-card">
                <h3 className="dash-card-title">This month</h3>
                <div className="stat-grid">
                  <div className="stat-row">
                    <span className="stat-row-label">
                      <span className="stat-icon stat-icon--yes" aria-hidden>
                        ✓
                      </span>
                      Present
                    </span>
                    <strong>{dashboardStats.present}</strong>
                  </div>
                  <div className="stat-row">
                    <span className="stat-row-label">
                      <span className="stat-icon stat-icon--no" aria-hidden>
                        ✗
                      </span>
                      Missed
                    </span>
                    <strong>{dashboardStats.missed}</strong>
                  </div>
                  <div className="stat-row">
                    <span className="stat-row-label">
                      <span className="stat-icon stat-icon--neutral" aria-hidden>
                        ◷
                      </span>
                      Marked days
                    </span>
                    <strong>{dashboardStats.present + dashboardStats.missed}</strong>
                  </div>
                </div>
              </div>

              <div className="dash-card">
                <h3 className="dash-card-title">Streak</h3>
                <div className="streak-block">
                  <div className="streak-line">
                    <span>Current streak</span>
                    <strong>{dashboardStats.streak} days</strong>
                  </div>
                  <div className="streak-line">
                    <span>Best (this month)</span>
                    <strong>{dashboardStats.bestInMonth} days</strong>
                  </div>
                </div>
              </div>

              <div className="dash-card">
                <h3 className="dash-card-title">This week</h3>
                <div className="week-list">
                  {weekRows.map(({ label, date, state }) => (
                    <div key={date} className="week-row">
                      <span className="week-row-day">{label}</span>
                      <span
                        className={`week-row-state week-row-state--${state === "yes" ? "yes" : state === "no" ? "no" : state === "na" ? "na" : "unset"}`}
                        title={date}
                      >
                        {state === "yes"
                          ? "✓"
                          : state === "no"
                            ? "✗"
                            : state === "na"
                              ? "—"
                              : "·"}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="sidebar-actions">
                <button
                  type="button"
                  className="btn btn-success"
                  disabled={!canMarkToday}
                  onClick={() => void markTodayPresent()}
                >
                  <span aria-hidden>✓</span>
                  Mark today present
                </button>
                <button type="button" className="btn btn-danger-ghost" onClick={logout}>
                  Log out
                </button>
              </div>
            </aside>

            <section className="card card--center calendar-card">
              <div className="calendar-card-header">
                <h2>Calendar</h2>
                <p className="calendar-sub">
                  Click a day to cycle: Yes → No → Clear. Changes save automatically.
                </p>
              </div>

              <div className="cal-nav">
                <button
                  type="button"
                  className="btn-icon-nav"
                  aria-label="Previous month"
                  onClick={goPrevMonth}
                  disabled={calendarBusy}
                >
                  ‹
                </button>
                <div className="cal-nav-title">
                  <p className="cal-nav-month">{monthTitle}</p>
                  <div className="cal-nav-jump">
                    <select
                      value={viewMonth}
                      onChange={(e) => setViewMonth(Number(e.target.value))}
                      aria-label="Jump to month"
                    >
                      {MONTH_OPTIONS.map(({ value, label: lab }) => (
                        <option key={value} value={value}>
                          {lab}
                        </option>
                      ))}
                    </select>
                    <select
                      value={viewYear}
                      onChange={(e) => setViewYear(Number(e.target.value))}
                      aria-label="Jump to year"
                    >
                      {yearOptions.map((y) => (
                        <option key={y} value={y}>
                          {y}
                        </option>
                      ))}
                    </select>
                  </div>
                </div>
                <button
                  type="button"
                  className="btn-icon-nav"
                  aria-label="Next month"
                  onClick={goNextMonth}
                  disabled={calendarBusy}
                >
                  ›
                </button>
              </div>

              <div className="calendar">
                {WEEKDAY_HEADERS.map((label) => (
                  <div key={label} className="calendar-dow" aria-hidden>
                    {label}
                  </div>
                ))}
                {Array.from({ length: calendarCells.leadingBlanks }, (_, i) => (
                  <div key={`pad-${i}`} className="calendar-pad" aria-hidden />
                ))}
                {calendarCells.days.map(({ day, date }) => {
                  const cls = ["day"];
                  if (monthData[date] === true) cls.push("yes");
                  if (monthData[date] === false) cls.push("no");
                  if (date === todayKey) cls.push("today");

                  const stateLabel =
                    monthData[date] === true
                      ? "Yes"
                      : monthData[date] === false
                        ? "No"
                        : "Not set";

                  const todayPart = date === todayKey ? ", today" : "";

                  return (
                    <button
                      type="button"
                      key={date}
                      className={cls.join(" ")}
                      disabled={calendarBusy}
                      aria-label={`${date}${todayPart}, ${stateLabel}. Click to cycle yes, no, or clear.`}
                      onClick={() => void cycleAttendance(date)}
                    >
                      <span className="day-num">{day}</span>
                      {monthData[date] === true ? (
                        <span className="day-icon day-icon--yes" aria-hidden>
                          ✓
                        </span>
                      ) : null}
                      {monthData[date] === false ? (
                        <span className="day-icon day-icon--no" aria-hidden>
                          ✗
                        </span>
                      ) : null}
                    </button>
                  );
                })}
              </div>

              <div className="calendar-summary" aria-live="polite">
                <span>
                  Present: <strong>{dashboardStats.present}</strong>
                </span>
                <span>
                  Missed: <strong>{dashboardStats.missed}</strong>
                </span>
                <span>
                  Streak: <strong>{dashboardStats.streak} days</strong>
                </span>
              </div>

              {calendarError ? (
                <div className="message error">{calendarError}</div>
              ) : null}
              <p className="auto-save-note">Attendance updates sync to your account immediately.</p>
            </section>

            <aside className="dash-sidebar dash-sidebar--right">
              <div className="dash-card">
                <h3 className="dash-card-title">Suggestions</h3>
                <ul className="insight-list">
                  <li>Mark rest days as “No” so your month stays honest.</li>
                  <li>Use “Mark today present” after your session for a one-tap log.</li>
                  <li>Switch months with the arrows to review past consistency.</li>
                </ul>
              </div>

              <div className="dash-card">
                <h3 className="dash-card-title">Goal</h3>
                <div className="goal-progress-wrap">
                  <div className="goal-progress-label">
                    <span>
                      Target: <strong>{MONTHLY_GOAL}</strong> days / month
                    </span>
                    <span>
                      <strong>{dashboardStats.present}</strong> / {MONTHLY_GOAL}
                    </span>
                  </div>
                  <div
                    className="goal-bar"
                    role="progressbar"
                    aria-valuenow={dashboardStats.present}
                    aria-valuemin={0}
                    aria-valuemax={MONTHLY_GOAL}
                    aria-label={`Monthly goal progress, ${dashboardStats.present} of ${MONTHLY_GOAL} days`}
                  >
                    <div className="goal-bar-fill" style={{ width: `${goalPct}%` }} />
                  </div>
                </div>
              </div>

              <div className="dash-card">
                <h3 className="dash-card-title">Plan ahead</h3>
                <ul className="placeholder-list">
                  <li>Workout plans and reminders will appear here.</li>
                </ul>
              </div>

              <div className="dash-card">
                <h3 className="dash-card-title">Coming soon</h3>
                <div className="feature-chips">
                  <div className="feature-chip">Weekly and yearly summaries</div>
                  <div className="feature-chip">Insights by weekday</div>
                  <div className="feature-chip">Export and sharing</div>
                </div>
              </div>
            </aside>
          </>
        ) : null}
      </main>
    </div>
  );
}
