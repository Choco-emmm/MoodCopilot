-- Ensure a usable admin account for local/dev environment

-- Promote common test users to admin when they exist
UPDATE users
SET role = 'ADMIN', status = 1
WHERE email IN ('test@test.com', 'testuser2@test.com');

-- Create fallback admin account if it does not exist
INSERT INTO users (display_name, email, password_hash, status, role)
SELECT
  '系统管理员',
  'admin@moodcopilot.local',
  '$2a$10$kFpYwG.183oRgDSQAa4Bw.OLwtOZ6.Tj6uWPMUmZzjX3UdSCFEP/.',
  1,
  'ADMIN'
WHERE NOT EXISTS (
  SELECT 1 FROM users WHERE email = 'admin@moodcopilot.local'
);

-- Keep fallback admin role stable
UPDATE users
SET role = 'ADMIN', status = 1
WHERE email = 'admin@moodcopilot.local';
