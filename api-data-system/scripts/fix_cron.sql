UPDATE scheduled_task
SET cron_expression = '0 0 */5 * * *'
WHERE cron_expression = '0 */5 * * *';

SELECT * FROM scheduled_task;
