-- JanSeva seed data: roles, categories, departments, zones/wards
-- Passwords/accounts are NOT seeded here; the default admin is created by
-- the application's AdminBootstrap on first startup from environment variables.

INSERT INTO roles (name, description) VALUES
    ('CITIZEN', 'Regular citizen reporting civic issues'),
    ('ADMIN', 'Platform administrator'),
    ('SUPERVISOR', 'Supervisor who reviews and assigns complaints'),
    ('DEPARTMENT', 'Department official who works on assigned complaints');

INSERT INTO complaint_categories (code, parent_id, name, description, duplicate_radius_m) VALUES
    ('GARBAGE', NULL, 'Garbage accumulation', 'Garbage or waste piled up in public areas', 300),
    ('ROAD_POTHOLE', NULL, 'Road potholes', 'Potholes on roads', 150),
    ('DAMAGED_ROAD', NULL, 'Damaged roads', 'Cracked, broken or severely damaged road surface', 200),
    ('WATER_LEAKAGE', NULL, 'Water leakage', 'Leaking water pipes or supply lines', 250),
    ('DRAINAGE_BLOCKAGE', NULL, 'Drainage blockage', 'Blocked drains causing overflow', 250),
    ('MANHOLE_OVERFLOW', NULL, 'Overflowing manholes', 'Manholes overflowing with sewage', 250),
    ('STREETLIGHT', NULL, 'Streetlight failures', 'Non-working streetlights', 150),
    ('OPEN_DRAIN', NULL, 'Open drains', 'Uncovered or broken drain channels', 250),
    ('BROKEN_FOOTPATH', NULL, 'Broken footpaths', 'Damaged pedestrian footpaths', 150),
    ('FALLEN_TREE', NULL, 'Fallen trees', 'Trees fallen on roads or public property', 300),
    ('PUBLIC_PROPERTY_DAMAGE', NULL, 'Public property damage', 'Damaged public infrastructure or property', 200),
    ('WATERLOGGING', NULL, 'Waterlogging', 'Stagnant water accumulation on roads', 300),
    ('OTHER', NULL, 'Other civic issues', 'Any other civic infrastructure issue', 250);

INSERT INTO departments (name, code, description, default_category_id) VALUES
    ('Sanitation Department', 'SANITATION', 'Garbage collection and solid waste management', (SELECT id FROM complaint_categories WHERE code = 'GARBAGE')),
    ('Roads and Buildings', 'ROADS', 'Road maintenance, potholes and footpaths', (SELECT id FROM complaint_categories WHERE code = 'ROAD_POTHOLE')),
    ('Water Supply Board', 'WATER_BOARD', 'Water supply and leakage', (SELECT id FROM complaint_categories WHERE code = 'WATER_LEAKAGE')),
    ('Storm Water Drainage', 'DRAINAGE', 'Drainage and manhole maintenance', (SELECT id FROM complaint_categories WHERE code = 'DRAINAGE_BLOCKAGE')),
    ('Electrical Department', 'ELECTRICAL', 'Streetlight and public electrical infrastructure', (SELECT id FROM complaint_categories WHERE code = 'STREETLIGHT')),
    ('Parks and Greenery', 'PARKS', 'Trees and public parks', (SELECT id FROM complaint_categories WHERE code = 'FALLEN_TREE')),
    ('Municipal General Works', 'GENERAL_WORKS', 'Other civic infrastructure', (SELECT id FROM complaint_categories WHERE code = 'OTHER'));

INSERT INTO zones (name, code, city, state) VALUES
    ('Kukatpally Zone', 'KPZ', 'Hyderabad', 'Telangana'),
    ('Serilingampally Zone', 'SLZ', 'Hyderabad', 'Telangana'),
    ('LB Nagar Zone', 'LBN', 'Hyderabad', 'Telangana');

INSERT INTO wards (zone_id, name, code) VALUES
    ((SELECT id FROM zones WHERE code = 'KPZ'), 'Kukatpally Ward 1', 'KPW-01'),
    ((SELECT id FROM zones WHERE code = 'KPZ'), 'Kukatpally Ward 2', 'KPW-02'),
    ((SELECT id FROM zones WHERE code = 'SLZ'), 'Miyapur Ward', 'SLW-01'),
    ((SELECT id FROM zones WHERE code = 'SLZ'), 'Chandanagar Ward', 'SLW-02'),
    ((SELECT id FROM zones WHERE code = 'LBN'), 'Uppal Ward', 'LBW-01');
