ALTER TABLE scheduling_slot DROP CHECK ck_slot_status;
ALTER TABLE scheduling_slot ADD CONSTRAINT ck_slot_status CHECK (status IN ('OPEN','BOOKED','CLOSED'));
CREATE INDEX ix_slot_coach_day_status ON scheduling_slot (coach_id,local_date,status,start_at_utc,id);
CREATE INDEX ix_booking_coach_day_status_slot ON bookings_request (coach_id,local_date_snapshot,status,slot_id);
