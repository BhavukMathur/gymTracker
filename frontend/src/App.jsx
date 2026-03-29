import { useCallback, useEffect, useMemo, useState } from "react";
import "./App.css";
import { apiBase, authHeader } from "./api.js";
import {
  clearStoredToken,
  getStoredToken,
  getUsernameFromToken,
  setStoredToken,
} from "./session.js";

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

  const base = apiBase();
  const todayKey = getTodayDateKey();

  const sessionUser = useMemo(() => getUsernameFromToken(token), [token]);

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

  const loggedIn = Boolean(token);

  useEffect(() => {
    if (!token) return;
    void loadMonth();
  }, [token, viewYear, viewMonth, loadMonth]);

  return (
    <div className="app-shell">
      <header className="app-header">
        <h1>Gym Attendance</h1>
        <p className="tagline">Track workouts by day</p>
      </header>

      <main className="app-main">
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

        <div className={loggedIn ? "card" : "hidden"}>
          <div className="user-bar">
            <div>
              <h3>Mark attendance</h3>
              {sessionUser ? (
                <span className="user-name">Signed in as {sessionUser}</span>
              ) : null}
            </div>
            <button type="button" className="btn btn-danger-ghost" onClick={logout}>
              Log out
            </button>
          </div>

          <p className="calendar-hint">
            Tap a day to cycle: <strong>Yes</strong> → <strong>No</strong> →{" "}
            <strong>Clear</strong>
          </p>

          <div className="month-picker">
            {/* <div className="month-picker-label">Viewing month</div> */}
            <div className="month-picker-fields">
              <label className="month-picker-field">
                <span className="month-picker-sublabel">Month</span>
                <select
                  className="month-select"
                  value={viewMonth}
                  onChange={(e) => setViewMonth(Number(e.target.value))}
                  aria-label="Month"
                >
                  {MONTH_OPTIONS.map(({ value, label }) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </label>
              <label className="month-picker-field">
                <span className="month-picker-sublabel">Year</span>
                <select
                  className="month-select"
                  value={viewYear}
                  onChange={(e) => setViewYear(Number(e.target.value))}
                  aria-label="Year"
                >
                  {yearOptions.map((y) => (
                    <option key={y} value={y}>
                      {y}
                    </option>
                  ))}
                </select>
              </label>
            </div>
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
                  aria-label={`${date}${todayPart}, ${stateLabel}. Tap to cycle yes, no, or clear.`}
                  onClick={() => void cycleAttendance(date)}
                >
                  {day}
                </button>
              );
            })}
          </div>

          {calendarError ? (
            <div className="message error">{calendarError}</div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
