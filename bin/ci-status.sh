#!/bin/bash

# Penn TotalRecall CircleCI Status Checker
# Checks current build status and shows live progress

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if CircleCI CLI is configured
if [ ! -f ~/.circleci/cli.yml ]; then
    echo -e "${RED}Error: CircleCI CLI not configured. Run 'circleci setup' first.${NC}"
    exit 1
fi

# Extract token from CLI config
TOKEN=$(grep 'token:' ~/.circleci/cli.yml | cut -d' ' -f2)
if [ -z "$TOKEN" ]; then
    echo -e "${RED}Error: No CircleCI token found in ~/.circleci/cli.yml${NC}"
    exit 1
fi

PROJECT_SLUG="gh/ldayton/Penn-TotalRecall"
API_BASE="https://circleci.com/api"

echo -e "${BLUE}=== Penn TotalRecall CI Status ===${NC}\n"

# Get latest pipeline
echo -e "${YELLOW}Fetching latest pipeline...${NC}"
LATEST_PIPELINE=$(curl -s -H "Circle-Token: $TOKEN" "$API_BASE/v2/project/$PROJECT_SLUG/pipeline" | jq '.items[0]')

if [ "$LATEST_PIPELINE" = "null" ]; then
    echo -e "${RED}Error: Could not fetch pipeline data${NC}"
    exit 1
fi

PIPELINE_ID=$(echo "$LATEST_PIPELINE" | jq -r '.id')
PIPELINE_NUMBER=$(echo "$LATEST_PIPELINE" | jq -r '.number')
PIPELINE_STATE=$(echo "$LATEST_PIPELINE" | jq -r '.state')
COMMIT_SUBJECT=$(echo "$LATEST_PIPELINE" | jq -r '.vcs.commit.subject')
COMMIT_HASH=$(echo "$LATEST_PIPELINE" | jq -r '.vcs.revision' | cut -c1-7)

echo -e "Pipeline #${PIPELINE_NUMBER} (${COMMIT_HASH}): ${COMMIT_SUBJECT}"
echo -e "State: ${PIPELINE_STATE}\n"

# Get workflow status
echo -e "${YELLOW}Checking workflow status...${NC}"
WORKFLOW=$(curl -s -H "Circle-Token: $TOKEN" "$API_BASE/v2/pipeline/$PIPELINE_ID/workflow" | jq '.items[0]')

if [ "$WORKFLOW" = "null" ]; then
    echo -e "${RED}No workflow found for this pipeline${NC}"
    exit 1
fi

WORKFLOW_ID=$(echo "$WORKFLOW" | jq -r '.id')
WORKFLOW_STATUS=$(echo "$WORKFLOW" | jq -r '.status')
WORKFLOW_NAME=$(echo "$WORKFLOW" | jq -r '.name')

case "$WORKFLOW_STATUS" in
    "success")
        echo -e "Workflow '${WORKFLOW_NAME}': ${GREEN}✅ SUCCESS${NC}"
        ;;
    "failed")
        echo -e "Workflow '${WORKFLOW_NAME}': ${RED}❌ FAILED${NC}"
        ;;
    "running")
        echo -e "Workflow '${WORKFLOW_NAME}': ${YELLOW}🔄 RUNNING${NC}"
        ;;
    *)
        echo -e "Workflow '${WORKFLOW_NAME}': ${WORKFLOW_STATUS}"
        ;;
esac

# Check job details using mixed API approach for best results
echo -e "\n${YELLOW}Checking recent job details (mixing v1.1 and v2.0 APIs)...${NC}"

# Get workflow jobs from v2 API (more reliable for job discovery)
if [ "$WORKFLOW_STATUS" = "running" ] || [ "$WORKFLOW_STATUS" = "failed" ]; then
    echo -e "\n${BLUE}Getting jobs from workflow...${NC}"
    WORKFLOW_JOBS=$(curl -s -H "Circle-Token: $TOKEN" "$API_BASE/v2/workflow/$WORKFLOW_ID/job")
    ACTIVE_JOB_NUM=$(echo "$WORKFLOW_JOBS" | jq -r '.items[0].job_number')
    
    if [ "$ACTIVE_JOB_NUM" != "null" ]; then
        echo -e "Found active job: #${ACTIVE_JOB_NUM}"
        JOB_LIST="$ACTIVE_JOB_NUM"
    else
        # Fallback to trying recent pipeline numbers
        JOB_LIST="$PIPELINE_NUMBER $(($PIPELINE_NUMBER-1)) $(($PIPELINE_NUMBER-2))"
    fi
else
    JOB_LIST="$PIPELINE_NUMBER $(($PIPELINE_NUMBER-1)) $(($PIPELINE_NUMBER-2))"
fi

# Now check each job using v1.1 API for detailed step info
for JOB_NUM in $JOB_LIST; do
    echo -e "\n${BLUE}--- Job #${JOB_NUM} Details (v1.1 API) ---${NC}"
    
    # Get detailed job info using v1.1 API (better for step details and logs)
    JOB_DETAILS=$(curl -s -H "Circle-Token: $TOKEN" "$API_BASE/v1.1/project/github/ldayton/Penn-TotalRecall/$JOB_NUM" 2>/dev/null)
    
    if [ $? -eq 0 ] && [ "$JOB_DETAILS" != "null" ] && echo "$JOB_DETAILS" | jq -e '.steps' >/dev/null 2>&1; then
        JOB_STATUS=$(echo "$JOB_DETAILS" | jq -r '.status // "unknown"')
        BUILD_TIME=$(echo "$JOB_DETAILS" | jq -r '.build_time_millis // 0')
        
        case "$JOB_STATUS" in
            "success")
                echo -e "Status: ${GREEN}✅ SUCCESS${NC} ($(($BUILD_TIME/1000))s)"
                ;;
            "failed")
                echo -e "Status: ${RED}❌ FAILED${NC} ($(($BUILD_TIME/1000))s)"
                ;;
            "running")
                echo -e "Status: ${YELLOW}🔄 RUNNING${NC} ($(($BUILD_TIME/1000))s so far)"
                ;;
            "queued"|"not_running")
                echo -e "Status: ${YELLOW}⏳ QUEUED${NC}"
                ;;
            *)
                echo -e "Status: ${JOB_STATUS}"
                ;;
        esac
        
        echo -e "\n${YELLOW}Build steps:${NC}"
        STEPS=$(echo "$JOB_DETAILS" | jq -r '.steps[]? | select(.name) | (.name + ": " + (.actions[0].status // "unknown"))')
        
        if [ -n "$STEPS" ]; then
            echo "$STEPS" | while IFS= read -r line; do
                if [[ $line == *": success"* ]]; then
                    echo -e "  ${GREEN}✅${NC} $line"
                elif [[ $line == *": failed"* ]]; then
                    echo -e "  ${RED}❌${NC} $line"
                elif [[ $line == *": running"* ]]; then
                    echo -e "  ${YELLOW}🔄${NC} $line"
                elif [[ $line == *": queued"* ]]; then
                    echo -e "  ${YELLOW}⏳${NC} $line"
                else
                    echo -e "  ⏸️  $line"
                fi
            done
            
            # Show additional info for running builds
            if [ "$JOB_STATUS" = "running" ]; then
                echo -e "\n${BLUE}💡 Build #${JOB_NUM} is actively running.${NC}"
                echo -e "   Duration: $(($BUILD_TIME/1000)) seconds"
                
                # Get live log output from currently running or recently completed steps
                RUNNING_STEP=$(echo "$JOB_DETAILS" | jq -r '.steps[] | select(.actions[0].status == "running") | .name' | head -1)
                FAILED_STEP=$(echo "$JOB_DETAILS" | jq -r '.steps[] | select(.actions[0].status == "failed") | .name' | head -1)
                
                TARGET_STEP="$RUNNING_STEP"
                if [ -z "$TARGET_STEP" ] && [ -n "$FAILED_STEP" ]; then
                    TARGET_STEP="$FAILED_STEP"
                fi
                
                if [ -n "$TARGET_STEP" ]; then
                    echo -e "\n${YELLOW}📋 Output from '${TARGET_STEP}':${NC}"
                    
                    # Try multiple approaches to get logs
                    # 1. Try v1.1 API output_url
                    OUTPUT_URL=$(echo "$JOB_DETAILS" | jq -r --arg step "$TARGET_STEP" '.steps[] | select(.name == $step) | .actions[0].output_url')
                    
                    if [ "$OUTPUT_URL" != "null" ] && [ -n "$OUTPUT_URL" ]; then
                        LOG_OUTPUT=$(curl -s -H "Circle-Token: $TOKEN" "$OUTPUT_URL" 2>/dev/null)
                        if [ -n "$LOG_OUTPUT" ]; then
                            echo "$LOG_OUTPUT" | tail -30 | sed 's/^/   /'
                            echo -e "\n${BLUE}💡 Last 30 lines via v1.1 API${NC}"
                        fi
                    fi
                    
                    # 2. If that fails, try v2 API for step details
                    if [ -z "$LOG_OUTPUT" ]; then
                        # Get step details from v2 API
                        V2_JOB=$(curl -s -H "Circle-Token: $TOKEN" "$API_BASE/v2/project/$PROJECT_SLUG/job/$JOB_NUM")
                        if [ $? -eq 0 ]; then
                            echo -e "   ${YELLOW}v1.1 logs unavailable, trying v2 API...${NC}"
                            # v2 API might have different log access patterns
                        else
                            echo -e "   ${YELLOW}Logs not accessible via API${NC}"
                        fi
                    fi
                    
                    # 3. Always show the web URL for full logs
                    echo -e "   ${BLUE}Full logs: https://circleci.com/gh/ldayton/Penn-TotalRecall/$JOB_NUM${NC}"
                fi
                break
            elif [ "$JOB_STATUS" = "failed" ]; then
                echo -e "\n${RED}💡 Build #${JOB_NUM} failed. Check logs at:${NC}"
                echo -e "   https://circleci.com/gh/ldayton/Penn-TotalRecall/$JOB_NUM"
                break
            elif [ "$JOB_STATUS" = "success" ]; then
                echo -e "\n${GREEN}💡 Build #${JOB_NUM} completed successfully!${NC}"
                break
            fi
        else
            echo -e "${YELLOW}No step details available yet (job may be queued)${NC}"
        fi
    else
        echo -e "${RED}Job #${JOB_NUM} not found or no access${NC}"
    fi
done

# Show web URL for more details
echo -e "\n${BLUE}🔗 Full details: https://circleci.com/gh/ldayton/Penn-TotalRecall${NC}"