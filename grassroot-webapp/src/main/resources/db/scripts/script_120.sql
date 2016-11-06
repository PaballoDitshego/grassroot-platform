CREATE TABLE group_message_stats
(
  id bigint,
  created_date_time timestamp without time zone,
  intended_group bigint,
  uid character varying(50) NOT NULL,
  number_intended_recepients bigint,
  number_times_read bigint,
  read boolean DEFAULT FALSE,
  
  CONSTRAINT group_message_stats_pkey PRIMARY KEY (id),
  CONSTRAINT fk_intended_group FOREIGN KEY (intended_group)
      REFERENCES public.group_profile (id) MATCH SIMPLE
      ON UPDATE NO ACTION ON DELETE NO ACTION,
  CONSTRAINT uk_message_stats_uid UNIQUE (uid)
)