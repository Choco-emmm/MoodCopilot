# MoodCopilot Roadmap

## Positioning

MoodCopilot is an AI emotion diary plus stranger-support community. It helps users understand their own emotions, and when they choose to share, connects them with people who have similar feelings.

## Phase 1: AI Diary + Same-Mood Support MVP

- Register and log in
- Create, view, and delete diary entries
- Diary visibility: private or public
- AI analysis after saving:
  - main emotion label
  - emotion intensity
  - topic labels
  - keywords
  - short summary
  - gentle response
- My diary list
- Public diary feed
- Recommend 3 similar public diaries after publishing
- Comments
- Resonance reaction
- Basic comment notifications
- Redis login/session support and API rate limiting

## Phase 2: Community and Recommendation

- Follow and unfollow users
- Following feed
- Comment replies
- Report, block, and hide
- Emotion/topic filters
- Same-mood feed
- Recommendation exposure records and deduplication
- AI-assisted comment rewriting
- Weekly reports

## Phase 3: AI Copilot

- AI chat for one diary entry
- AI chat for recent mood state
- Emotion goals
- 3-day and 7-day action plans
- Follow-up on plan execution
- Monthly reports
- Mood trend charts
- Trigger-factor statistics
- Sleep signal extraction from diary text
- Async AI analysis task queue

## Phase 4: Location and Life Context

- Manual city/school/area selection
- User-level location switch
- Diary-level nearby visibility switch
- Nearby feed
- Fuzzy location display
- Optional GPS authorization
- Clear location data

## Phase 5: Engineering Highlights

- Vector search for similar diaries
- Personal memory retrieval
- Wearable data import from CSV/JSON/ZIP
- Sleep and mood correlation analysis
- Admin console
- Content moderation workflow
- Recommendation metrics
- Docker deployment
- Load testing and SQL/JVM tuning notes

