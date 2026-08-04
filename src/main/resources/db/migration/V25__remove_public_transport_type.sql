UPDATE course
SET transport_type = 'WALK'
WHERE transport_type = 'PUBLIC';

ALTER TABLE course
DROP CHECK chk_course_transport_type;

ALTER TABLE course
    ADD CONSTRAINT chk_course_transport_type
        CHECK (transport_type IN ('WALK', 'CAR'));