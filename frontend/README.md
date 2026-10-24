# ChainLance frontend

React 19, Vite, TypeScript, Tailwind CSS 4, React Router, Axios, Lucide. Built against the backend as it exists in this repository, not against the plan in `ARCHITECTURE.md`.

## Run it (Windows PowerShell)

Needs Node 20.19 or newer (`node -v`).

```powershell
cd chainlance\frontend
npm install
Copy-Item .env.example .env
npm run dev
```

Open http://localhost:5173. The dev server is pinned to port 5173 because the backend's CORS setting only allows that origin.

Backend, in a second terminal (needs MySQL, a `backend\.env` and `JWT_SECRET` set):

```powershell
cd chainlance\backend
Copy-Item .env.example .env   # then fill in the values
.\run-local.ps1
```

Other commands: `npm run typecheck`, `npm run build`, `npm run preview`.

## What is real and what is pending

The in-app page at `/api-status` lists every endpoint and whether a controller exists for it. The same list lives in `src/services/endpoints.ts`.

| Area | Backend today | Frontend |
| --- | --- | --- |
| Register, login, `GET/PUT /api/users/me` | Live | Connected |
| Wallet challenge, verify, list | Live | Connected |
| Projects, proposals, negotiation, agreements | No controllers yet (entities and tables only) | Pages built on a service boundary; they show "not available in the backend yet" until the endpoints exist |
| Escrow, milestones, submissions, disputes, reviews, admin | Not built | Not built, nav slots reserved |

Request and response shapes for the pending areas are assumptions, isolated in `src/services/adapters.ts` and `src/types/marketplace.ts`. When the real DTOs land, change those two files and `endpoints.ts`.

## Sample data

`VITE_USE_MOCKS=true` swaps the pending services for clearly labelled in-memory sample data (`src/mocks`), with a banner on every page. Sign-in, profile and wallet always use the real API. The default is `false`, so nothing fake appears unless you turn it on.

## Safety rules the UI follows

- A wallet is "verified" only when `GET /api/wallet` returns it after the backend checked the signature. A connected MetaMask address is shown as connected, never as verified.
- An agreement is shown as final only when the backend's returned status says so.
- No private keys, seed phrases or secrets: `VITE_*` variables are public and contain only the API URL, a mock switch and a chain id.
- Role-based navigation is a convenience. The backend enforces permissions.

## Layout

```
src/
  components/{layout,ui,landing,projects,proposals,negotiation,agreements,wallet}
  pages/        one file per route
  routes/       route table and auth guards
  services/     api.ts (Axios + JWT), one service per area, endpoints.ts, adapters.ts
  mocks/        sample data, used only when VITE_USE_MOCKS=true
  context/ hooks/ types/ utils/ config/
```
